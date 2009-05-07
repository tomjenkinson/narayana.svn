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
 * (C) 2005-2009,
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
 * $Id: ConnectionManager.java 2342 2006-03-30 13:06:17Z  $
 */

package com.arjuna.ats.internal.jdbc;

import com.arjuna.ats.jdbc.TransactionalDriver;
import com.arjuna.ats.jdbc.logging.jdbcLogger;

import java.util.*;

import java.sql.SQLException;
import java.sql.Connection;
import java.lang.reflect.Constructor;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/*
 * Only ever create a single instance of a given connection, based upon the
 * user/password/url/dynamic_class options. If the connection we have cached
 * has been closed, then create a new one.
 */
public class ConnectionManager
{

    /*
     * Connections are pooled for the duration of a transaction.
     */

    /**
     * @message com.arjuna.ats.internal.jdbc.nojdbcimple Can't load ConnectionImple class.
     */
    public static synchronized Connection create (String dbUrl, Properties info) throws SQLException
    {
        String user = info.getProperty(TransactionalDriver.userName);
        String passwd = info.getProperty(TransactionalDriver.password);
        String dynamic = info.getProperty(TransactionalDriver.dynamicClass);
        if (dynamic == null)
            dynamic = "";

        for (ConnectionImple conn : _connections)
        {
            ConnectionControl connControl = conn.connectionControl();
            TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
            Transaction tx1, tx2 = null;

            tx1 = connControl.transaction();
            try
            {
                tx2 = tm.getTransaction();
            }
            catch (javax.transaction.SystemException se)
            {
                /* Ignore: tx2 is null already */
            }

            /* Check transaction and database connection. */
            if ((tx1 != null && tx1.equals(tx2))
                    && connControl.url().equals(dbUrl)
                    && connControl.user().equals(user)
                    && connControl.password().equals(passwd)
                    && connControl.dynamicClass().equals(dynamic))
            {
                try
                {
                    /*
                  * Should not overload the meaning of closed. Change!
                  */

                    if (!conn.isClosed())
                    {
                        // ConnectionImple does not actually implement Connection, but its
                        // concrete child classes do. See ConnectionImple javadoc.
                        return (Connection)conn;
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    SQLException sqlException = new SQLException(ex.getMessage());
                    sqlException.initCause(ex);
                    throw sqlException;
                }
            }
        }

        // the ConnectionImple subclass is loaded dynamically because we only have one or the
        // other available at build time, so we can't reference either directly in the code.
        // See ConnectionImple javadoc.

        String connectionImpleClassName = "com.arjuna.ats.internal.jdbc.ConnectionImpleJDBC4";
        ConnectionImple conn;
        try
        {
            Class clazz = Class.forName(connectionImpleClassName);
            Constructor ctor = clazz.getConstructor(new Class[] { String.class, Properties.class} );
            conn =  (ConnectionImple)ctor.newInstance(new Object[] { dbUrl, info });
        }
        catch(Exception exception)
        {
            // not necessarily an error, we may have a JDK5 build that does not include the JDBC4 driver.
            // try to fallback to the older driver
            connectionImpleClassName = "com.arjuna.ats.internal.jdbc.ConnectionImpleJDBC3";
            try
            {
                Class clazz = Class.forName(connectionImpleClassName);
                Constructor ctor = clazz.getConstructor(new Class[] { String.class, Properties.class} );
                conn =  (ConnectionImple)ctor.newInstance(new Object[] { dbUrl, info });
            }
            catch(Exception e)
            {
                if (jdbcLogger.logger.isErrorEnabled())
                {
                    jdbcLogger.logger.error(jdbcLogger.logMesg.getString("com.arjuna.ats.internal.jdbc.nojdbcimple")+" "+e.toString());
                }
                SQLException sqlException = new SQLException(jdbcLogger.logMesg.getString("com.arjuna.ats.internal.jdbc.nojdbcimple")+" "+e.toString());
                sqlException.initCause(e);
                throw sqlException;
            }
        }

        /*
       * Will replace any old (closed) connection which had the
       * same connection information.
       */

        _connections.add(conn);

        // ConnectionImple does not actually implement Connection, but its
        // concrete child classes do. See ConnectionImple javadoc.
        return (Connection)conn;
    }

    public static synchronized void remove (ConnectionImple conn)
    {
        _connections.remove(conn);
    }

    private static Set<ConnectionImple> _connections = new HashSet<ConnectionImple>();

}
