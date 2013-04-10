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
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;

import junit.framework.TestCase;

public class TestSetTransactionTimeout extends TestCase {
	public void test() throws NotSupportedException,
			SystemException, IllegalStateException, RollbackException {
		javax.transaction.TransactionManager tm = new TransactionManagerImple();

		tm.begin();

		javax.transaction.Transaction theTransaction = tm.getTransaction();
		theTransaction.enlistResource(new XAResource() {

			public void commit(Xid arg0, boolean arg1) throws XAException {
				// TODO Auto-generated method stub

			}

			public void end(Xid arg0, int arg1) throws XAException {
				// TODO Auto-generated method stub

			}

			public void forget(Xid arg0) throws XAException {
				// TODO Auto-generated method stub

			}

			public int getTransactionTimeout() throws XAException {
				// TODO Auto-generated method stub
				return 0;
			}

			public boolean isSameRM(XAResource arg0) throws XAException {
				// TODO Auto-generated method stub
				return false;
			}

			public int prepare(Xid arg0) throws XAException {
				// TODO Auto-generated method stub
				return 0;
			}

			public Xid[] recover(int arg0) throws XAException {
				// TODO Auto-generated method stub
				return null;
			}

			public void rollback(Xid arg0) throws XAException {
				// TODO Auto-generated method stub

			}

			public boolean setTransactionTimeout(int arg0) throws XAException {
				throw new XAException("foo bar");
			}

			public void start(Xid arg0, int arg1) throws XAException {
				// TODO Auto-generated method stub

			}
		});
		tm.rollback();
	}

}
