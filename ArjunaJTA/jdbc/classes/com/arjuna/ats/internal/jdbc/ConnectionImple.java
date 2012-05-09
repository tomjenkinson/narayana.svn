/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 *
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * Copyright (C) 1998, 1999, 2000, 2001,
 *
 * Arjuna Solutions Limited,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: ConnectionImple.java 2342 2006-03-30 13:06:17Z  $
 */

package com.arjuna.ats.internal.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.XAConnection;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;

import com.arjuna.ats.internal.jdbc.drivers.modifiers.ConnectionModifier;
import com.arjuna.ats.internal.jdbc.drivers.modifiers.ModifierFactory;
import com.arjuna.ats.jdbc.TransactionalDriver;
import com.arjuna.ats.jdbc.common.jdbcPropertyManager;
import com.arjuna.ats.jdbc.logging.jdbcLogger;
import com.arjuna.ats.jta.xa.RecoverableXAConnection;
import com.arjuna.ats.jta.xa.XAModifier;
import com.arjuna.common.util.logging.DebugLevel;
import com.arjuna.common.util.logging.VisibilityLevel;

/**
 * A transactional JDBC connection using InvocationHandling. This wraps the real connection and
 * registers it with the transaction at appropriate times to ensure that all
 * worked performed by it may be committed or rolled back.
 * 
 * Once a connection is used within a transaction, that instance is bound to
 * that transaction for the duration. It can be used by any number of threads,
 * as long as they all have the same notion of the "current" transaction. When
 * the transaction terminates, the connection is freed for use in another
 * transaction.
 * 
 * Applications must not use this class directly.
 * 
 * @author Mark Little (mark@arjuna.com)
 * @version $Id: ConnectionImple.java 2342 2006-03-30 13:06:17Z $
 * @since JTS 2.0.
 */

public class ConnectionImple implements InvocationHandler
{

	public ConnectionImple(String dbName, Properties info) throws SQLException
	{
		if (jdbcLogger.logger.isDebugEnabled())
		{
			jdbcLogger.logger.debug(DebugLevel.CONSTRUCTORS,
					VisibilityLevel.VIS_PUBLIC,
					com.arjuna.ats.jdbc.logging.FacilityCode.FAC_JDBC,
					"ConnectionImple.ConnectionImple ( " + dbName + " )");
		}

		String user = null;
		String passwd = null;
		String dynamic = null;

		if (info != null)
		{
			user = info.getProperty(TransactionalDriver.userName);
			passwd = info.getProperty(TransactionalDriver.password);
			dynamic = info.getProperty(TransactionalDriver.dynamicClass);
		}

		if ((dynamic == null) || (dynamic.equals("")))
		{
			_recoveryConnection = new IndirectRecoverableConnection(dbName,
					user, passwd, this);
		}
		else
		{
			_recoveryConnection = new DirectRecoverableConnection(dbName, user,
					passwd, dynamic, this);
		}

		/*
		 * Is there any "modifier" we are required to work with?
		 */

		_theModifier = null;
		_theConnection = null;
	}

	public ConnectionImple(String dbName, String user, String passwd)
			throws SQLException
	{
		this(dbName, user, passwd, null);
	}

	public ConnectionImple(String dbName, String user, String passwd,
			String dynamic) throws SQLException
	{
		if (jdbcLogger.logger.isDebugEnabled())
		{
			jdbcLogger.logger.debug(DebugLevel.CONSTRUCTORS,
					VisibilityLevel.VIS_PUBLIC,
					com.arjuna.ats.jdbc.logging.FacilityCode.FAC_JDBC,
					"ConnectionImple.ConnectionImple ( " + dbName + ", " + user
							+ ", " + passwd + ", " + dynamic + " )");
		}

		if ((dynamic == null) || (dynamic.equals("")))
		{
			_recoveryConnection = new IndirectRecoverableConnection(dbName,
					user, passwd, this);
		}
		else
		{
			_recoveryConnection = new DirectRecoverableConnection(dbName, user,
					passwd, dynamic, this);
		}

		/*
		 * Any "modifier" required to work with?
		 */

		_theModifier = null;
		_theConnection = null;
	}



	/**
	 * Not allowed if within a transaction.
	 * 
	 * @message com.arjuna.ats.internal.jdbc.autocommit AutoCommit is not
	 *          allowed by the transaction service.
	 */

	public void setAutoCommit(boolean autoCommit) throws SQLException
	{
		if (transactionRunning())
		{
			if (autoCommit)
				throw new SQLException(jdbcLogger.logMesg
						.getString("com.arjuna.ats.internal.jdbc.autocommit"));
		}
		else
		{
			getConnection().setAutoCommit(autoCommit);
		}
	}

	/**
	 * @message com.arjuna.ats.internal.jdbc.commiterror Commit not allowed by
	 *          transaction service.
	 */

	public void commit() throws SQLException
	{
		/*
		 * If there is a transaction running, then it cannot be terminated via
		 * the driver - the user must go through current.
		 */

		if (transactionRunning())
		{
			throw new SQLException(jdbcLogger.logMesg
					.getString("com.arjuna.ats.internal.jdbc.commiterror"));
		}
		else
			getConnection().commit();
	}

	/**
	 * @message com.arjuna.ats.internal.jdbc.aborterror Rollback not allowed by
	 *          transaction service.
	 */

	public void rollback() throws SQLException
	{
		if (transactionRunning())
		{
			throw new SQLException(jdbcLogger.logMesg
					.getString("com.arjuna.ats.internal.jdbc.aborterror"));
		}
		else
			getConnection().rollback();
	}

	/*
	 * This needs to be reworked in light of experience and requirements.
	 */

	/**
	 * @message com.arjuna.ats.internal.jdbc.delisterror Delist of resource
	 *          failed.
	 * @message com.arjuna.ats.internal.jdbc.closeerror An error occurred during
	 *          close:
	 */

	public void close() throws SQLException
	{
		try
		{
			/*
			 * Need to know whether this particular connection has outstanding
			 * resources waiting for it. If not then we can close, otherwise we
			 * can't.
			 */

			if (!_recoveryConnection.inuse())
			{
				ConnectionManager.remove(this); // finalize?
			}

			/*
			 * Delist resource if within a transaction.
			 */

			javax.transaction.TransactionManager tm = com.arjuna.ats.jta.TransactionManager
					.transactionManager();
			javax.transaction.Transaction tx = tm.getTransaction();

			/*
			 * Don't delist if transaction not running. Rely on exception for
			 * this. Also only delist if the transaction is the one the
			 * connection is enlisted with!
			 */

			if (tx != null)
			{
				if (_recoveryConnection.validTransaction(tx))
				{
					XAResource xares = _recoveryConnection.getResource();

					if (!tx.delistResource(xares, XAResource.TMSUCCESS))
						throw new SQLException(
								jdbcLogger.logMesg
										.getString("com.arjuna.ats.internal.jdbc.delisterror"));

					/*
					 * We can't close the connection until the transaction has
					 * terminated, so register a synchronisation here.
					 */
					getModifier();

					if (_theModifier != null
							&& ((ConnectionModifier) _theModifier)
									.supportsMultipleConnections())
					{
						tx
								.registerSynchronization(new ConnectionSynchronization(
										_theConnection, _recoveryConnection));
						_theConnection = null;
					}
				}
			}
			else
			{
				_recoveryConnection.closeCloseCurrentConnection();
                if (_theConnection != null && !_theConnection.isClosed())
                    _theConnection.close();
                
                _theConnection = null;
			}

			// what about connections without xaCon?
		}
		catch (IllegalStateException ex)
		{
			// transaction not running, so ignore.
		}
		catch (SQLException sqle)
		{
			throw sqle;
		}
		catch (Exception e1)
		{
			e1.printStackTrace();

			throw new SQLException(jdbcLogger.logMesg
					.getString("com.arjuna.ats.internal.jdbc.closeerror")
					+ e1);
		}
	}

	public boolean isClosed() throws SQLException
	{
		/*
		 * A connection may appear closed to a thread if another thread has
		 * bound it to a different transaction.
		 */

		checkTransaction();

		if (_theConnection == null)
			return false; // not opened yet.
		else
			return _theConnection.isClosed();
	}

	/**
	 * Can only set readonly before we use the connection in a given
	 * transaction!
	 * 
	 * @message com.arjuna.ats.internal.jdbc.setreadonly Cannot set readonly
	 *          when within a transaction!
	 */

	public void setReadOnly(boolean ro) throws SQLException
	{
		if (!_recoveryConnection.inuse())
		{
			getConnection().setReadOnly(ro);
		}
		else
			throw new SQLException(jdbcLogger.logMesg
					.getString("com.arjuna.ats.internal.jdbc.setreadonly"));
	}

	/**
	 * @return the Arjuna specific recovery connection information. This should
	 *         not be used by anything other than Arjuna.
	 */

	public final RecoverableXAConnection recoveryConnection()
	{
		return _recoveryConnection;
	}

	/**
	 * @return the XAResource associated with the current XAConnection.
	 */

	protected final XAResource getXAResource()
	{
		try
		{
			return _recoveryConnection.getResource();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * Remove this connection so that we have to get another one when asked.
	 * Some drivers allow connections to be reused once any transactions have
	 * finished with them.
	 */

	final void reset()
	{
		try
		{
			if (_theConnection != null)
				_theConnection.close();
		}
		catch (Exception ex)
		{
		}
		finally
		{
			_theConnection = null;
		}
	}

	/**
	 * @message com.arjuna.ats.internal.jdbc.isolationlevelfailget {0} - failed
	 *          to set isolation level: {1}
	 * @message com.arjuna.ats.internal.jdbc.isolationlevelfailset {0} - failed
	 *          to set isolation level: {1}
	 * @message com.arjuna.ats.internal.jdbc.conniniterror JDBC2 connection
	 *          initialisation problem
	 */

	final java.sql.Connection getConnection() throws SQLException
	{
		if (_theConnection != null && !_theConnection.isClosed())
			return _theConnection;

		XAConnection xaConn = _recoveryConnection.getConnection();

		if (xaConn != null)
		{
			_theConnection = xaConn.getConnection();

			try
			{
				getModifier();

				if (_theModifier != null)
				{
					((ConnectionModifier) _theModifier).setIsolationLevel(
							_theConnection, _currentIsolationLevel);
				}
			}
			catch (SQLException ex)
			{
				throw ex;
			}
			catch (Exception e)
			{
				if (jdbcLogger.loggerI18N.isWarnEnabled())
				{
					jdbcLogger.loggerI18N
							.warn(
									"com.arjuna.ats.internal.jdbc.isolationlevelfailset",
									new Object[]
									{ "ConnectionImple.getConnection", e });
				}

				throw new SQLException(
						jdbcLogger.logMesg
								.getString("com.arjuna.ats.internal.jdbc.conniniterror")
								+ ":" + e);
			}

			return _theConnection;
		}
		else
			return null;
	}

	final ConnectionControl connectionControl()
	{
		return (ConnectionControl) _recoveryConnection;
	}

	protected final boolean transactionRunning() throws SQLException
	{
		try
		{
			if (com.arjuna.ats.jta.TransactionManager.transactionManager()
					.getTransaction() != null)
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		catch (Exception e)
		{
			throw new SQLException(e.toString());
		}
	}

	/**
	 * Whenever a JDBC call is invoked on us we get an XAResource and try to
	 * register it with the transaction. If the same thread causes this to
	 * happen many times within the same transaction then we will currently
	 * attempt to get and register many redundant XAResources for it. The JTA
	 * implementation will detect this and ignore all but the first for each
	 * thread. However, a further optimisation would be to trap such calls here
	 * and not do a registration at all. This would require the connection
	 * object to be informed whenever a transaction completes so that it could
	 * flush its cache of XAResources though.
	 * 
	 * @message com.arjuna.ats.internal.jdbc.rollbackerror {0} - could not mark
	 *          transaction rollback
	 * @message com.arjuna.ats.internal.jdbc.enlistfailed enlist of resource
	 *          failed
	 * @message com.arjuna.ats.internal.jdbc.alreadyassociated Connection is
	 *          already associated with a different transaction! Obtain a new
	 *          connection for this transaction.
	 */

	protected final synchronized void registerDatabase() throws SQLException
	{
		if (jdbcLogger.logger.isDebugEnabled())
		{
			jdbcLogger.logger.debug(DebugLevel.FUNCTIONS,
					VisibilityLevel.VIS_PRIVATE,
					com.arjuna.ats.jdbc.logging.FacilityCode.FAC_JDBC,
					"ConnectionImple.registerDatabase ()");
		}

		Connection theConnection = getConnection();

		if (theConnection != null)
		{
			XAResource xares = null;

			try
			{
				javax.transaction.TransactionManager tm = com.arjuna.ats.jta.TransactionManager
						.transactionManager();
				javax.transaction.Transaction tx = tm.getTransaction();

				if (tx == null)
					return;

				/*
				 * Already enlisted with this transaction?
				 */

				if (!_recoveryConnection.setTransaction(tx))
					throw new SQLException(
							jdbcLogger.logMesg
									.getString("com.arjuna.ats.internal.jdbc.alreadyassociated"));

				Object[] params;

				if (_theModifier != null)
					params = new Object[2];
				else
					params = new Object[1];

				params[com.arjuna.ats.jta.transaction.Transaction.XACONNECTION] = _recoveryConnection;

				if (_theModifier != null)
					params[com.arjuna.ats.jta.transaction.Transaction.XAMODIFIER] = (XAModifier) _theModifier;

				/*
				 * Use our extended version of enlistResource.
				 */

				xares = _recoveryConnection.getResource();

				if (!((com.arjuna.ats.jta.transaction.Transaction) tx)
						.enlistResource(xares, params))
				{
					/*
					 * Failed to enlist, so mark transaction as rollback only.
					 */

					try
					{
						tx.setRollbackOnly();
					}
					catch (Exception e)
					{
						if (jdbcLogger.loggerI18N.isWarnEnabled())
						{
							jdbcLogger.loggerI18N
									.warn(
											"com.arjuna.ats.internal.jdbc.rollbackerror",
											new Object[]
											{ "ConnectionImple.registerDatabase" });
						}

						throw new SQLException(e.toString());
					}

					throw new SQLException(
							"ConnectionImple.registerDatabase - "
									+ jdbcLogger.logMesg
											.getString("com.arjuna.ats.internal.jdbc.enlistfailed"));
				}

				params = null;
				xares = null;
				tx = null;
				tm = null;
			}
			catch (RollbackException e1)
			{
				throw new SQLException("ConnectionImple.registerDatabase - "
						+ e1);
			}
			catch (SystemException e2)
			{
				throw new SQLException("ConnectionImple.registerDatabase - "
						+ e2);
			}
			catch (SQLException e3)
			{
				throw e3;
			}
			catch (Exception e4)
			{
				throw new SQLException(e4.toString());
			}
		}
	}

	/**
	 * @message com.arjuna.ats.internal.jdbc.alreadyassociatedcheck Checking
	 *          transaction and found that this connection is already associated
	 *          with a different transaction! Obtain a new connection for this
	 *          transaction.
	 * @message com.arjuna.ats.internal.jdbc.infoerror Could not get transaction
	 *          information.
	 * @message com.arjuna.ats.internal.jdbc.inactivetransaction Transaction is
	 * not active on the thread!
	 */

	protected final void checkTransaction() throws SQLException
	{
		if (jdbcLogger.logger.isDebugEnabled())
		{
			jdbcLogger.logger.debug(DebugLevel.FUNCTIONS,
					VisibilityLevel.VIS_PRIVATE,
					com.arjuna.ats.jdbc.logging.FacilityCode.FAC_JDBC,
					"ConnectionImple.checkTransaction ()");
		}

		try
		{
			javax.transaction.TransactionManager tm = com.arjuna.ats.jta.TransactionManager
					.transactionManager();
			javax.transaction.Transaction tx = tm.getTransaction();

			if (tx == null)
				return;
			
			if (tx.getStatus() != Status.STATUS_ACTIVE)
				throw new SQLException(jdbcLogger.logMesg
						.getString("com.arjuna.ats.internal.jdbc.inactivetransaction"));

			/*
			 * Now check that we are not already associated with a transaction.
			 */

			if (!_recoveryConnection.validTransaction(tx))
				throw new SQLException(
						jdbcLogger.logMesg
								.getString("com.arjuna.ats.internal.jdbc.alreadyassociatedcheck"));
		}
		catch (SQLException ex)
		{
			throw ex;
		}
		catch (Exception e3)
		{
			throw new SQLException(jdbcLogger.logMesg
					.getString("com.arjuna.ats.internal.jdbc.infoerror"));
		}
	}

	/**
	 * @message com.arjuna.ats.internal.jdbc.getmoderror Failed to get modifier
	 *          for driver:
	 */

	private final void getModifier()
	{
		if (_theModifier == null)
		{
			try
			{
				DatabaseMetaData md = _theConnection.getMetaData();

				String name = md.getDriverName();
				int major = md.getDriverMajorVersion();
				int minor = md.getDriverMinorVersion();

				_theModifier = ModifierFactory.getModifier(name, major, minor);

				((ConnectionControl) _recoveryConnection)
						.setModifier((ConnectionModifier) _theModifier);
			}
			catch (Exception ex)
			{
				if (jdbcLogger.loggerI18N.isWarnEnabled())
				{
					jdbcLogger.loggerI18N.warn(
							"com.arjuna.ats.internal.jdbc.getmoderror", ex);
				}
			}
		}
	}

	private RecoverableXAConnection _recoveryConnection;

	private java.lang.Object _theModifier;

	private Connection _theConnection;

	private static final int defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE;

	private static int _currentIsolationLevel = defaultIsolationLevel;

	/**
	 * @message com.arjuna.ats.internal.jdbc.isolationerror Unknown isolation
	 *          level {0}. Will use default of TRANSACTION_SERIALIZABLE.
	 */

	static
	{
		String isolationLevel = jdbcPropertyManager.propertyManager
				.getProperty(com.arjuna.ats.jdbc.common.Environment.ISOLATION_LEVEL);

		if (isolationLevel != null)
		{
			if (isolationLevel.equals("TRANSACTION_READ_COMMITTED"))
				_currentIsolationLevel = Connection.TRANSACTION_READ_COMMITTED;
			else if (isolationLevel.equals("TRANSACTION_READ_UNCOMMITTED"))
				_currentIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED;
			else if (isolationLevel.equals("TRANSACTION_REPEATABLE_READ"))
				_currentIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ;
			else if (isolationLevel.equals("TRANSACTION_SERIALIZABLE"))
				_currentIsolationLevel = Connection.TRANSACTION_SERIALIZABLE;
			else
			{
				if (jdbcLogger.loggerI18N.isWarnEnabled())
				{
					jdbcLogger.loggerI18N.warn(
							"com.arjuna.ats.internal.jdbc.isolationerror",
							new Object[]
							{ isolationLevel });
				}

				_currentIsolationLevel = Connection.TRANSACTION_SERIALIZABLE;
			}
		}
	}

	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

		if (method.getName().equals("setAutoCommit")) {
			setAutoCommit((Boolean) args[0]);
			return null;
		} else if (method.getName().equals("commit")) {
			commit();
			return null;
		} else if (method.getName().equals("rollback")) {
			rollback();
			return null;
		} else if (method.getName().equals("close")) {
			close();
			return null;
		} else if (method.getName().equals("isClosed")) {
			return isClosed();
		} else if (method.getName().equals("setReadOnly")) {
			setReadOnly((Boolean) args[0]);
			return null;
		} else {
			if ((method.getName().equals("setSavepoint")
					|| method.getName().equals("rollback") || method.getName()
					.equals("releaseSavepoint"))) {
				if (transactionRunning())
					throw new SQLException(
							jdbcLogger.logMesg
									.getString("com.arjuna.ats.internal.jdbc.releasesavepointerror"));
				// Don't do checkTransaction or registerDatabase
			} else if (method.getName().equals("setSchema")
					|| method.getName().equals("getSchema")
					|| method.getName().equals("abort")
					|| method.getName().equals("setNetworkTimeout")
					|| method.getName().equals("getNetworkTimeout")) {
				throw new SQLException("Method " + method.getName()
						+ " not supported");
			} else if (method.getName().equals("getTypeMap")
					|| method.getName().equals("setTypeMap")
					|| method.getName().equals("getAutoCommit")
					|| method.getName().equals("getMetaData")
					|| method.getName().equals("isReadOnly")
					|| method.getName().equals("getTransactionIsolation")
					|| method.getName().equals("getWarnings")
					|| method.getName().equals("clearWarnings")
					|| method.getName().equals("getClientInfo")
					|| method.getName().equals("setClientInfo")
					|| method.getName().equals("isWrapperFor")
					|| method.getName().equals("unwrap")) {
				// Don't do checkTransaction or registerDatabase
			} else if (method.getName().equals("setTransactionIsolation")
					|| method.getName().equals("setTypeMap")
					|| method.getName().equals("getAutoCommit")
					|| method.getName().equals("getMetaData")
					|| method.getName().equals("isReadOnly")
					|| method.getName().equals("getTransactionIsolation")
					|| method.getName().equals("getWarnings")
					|| method.getName().equals("clearWarnings")
					|| method.getName().equals("getClientInfo")
					|| method.getName().equals("setClientInfo")) {
				// Don't do registerDatabase
				checkTransaction();
			} else {
				checkTransaction();

				registerDatabase();
			}

			try {
				return (Boolean) method.invoke(getConnection(), args);
			} catch (Throwable e) {
				SQLException e2 = new SQLException("Could not call isValid:"
						+ e.getMessage());
				if (e.getCause() instanceof SQLException) {
					e2.setNextException((SQLException) e.getCause());
				} else {
					e.printStackTrace();
				}
				throw e2;
			}
		}
	}

}
