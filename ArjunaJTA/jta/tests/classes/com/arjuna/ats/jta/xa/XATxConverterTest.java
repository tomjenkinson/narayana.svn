package com.arjuna.ats.jta.xa;

import static org.junit.Assert.assertEquals;

import javax.transaction.xa.Xid;

import org.junit.Test;

import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.common.arjPropertyManager;
import com.arjuna.ats.arjuna.coordinator.TxControl;

public class XATxConverterTest {

	@Test
	public void testXAConverter() throws CoreEnvironmentBeanException {
		Uid uid = new Uid();
		boolean branch = true;
		String eisName = "foo";
		arjPropertyManager.getCoreEnvironmentBean().setNodeIdentifier(1);

		XidImple rootXid = new XidImple(uid, branch, eisName);
		{
			assertEquals(XATxConverter.getNodeName(rootXid.getXID()), 1);
			assertEquals(XATxConverter.getEISName(rootXid.getXID()), eisName);
			assertEquals(XATxConverter.getSubordinateNodeName(rootXid.getXID()), 1);
			assertEquals(XATxConverter.getParentNodeName(rootXid.getXID()), 1);
		}

		TxControl.setXANodeName(2);
		XidImple subordinateXid = new XidImple(rootXid, true);
		{
			assertEquals(XATxConverter.getNodeName(subordinateXid.getXID()), 1);
			assertEquals(XATxConverter.getEISName(subordinateXid.getXID()), eisName);
			assertEquals(XATxConverter.getSubordinateNodeName(subordinateXid.getXID()), 2);
			assertEquals(XATxConverter.getParentNodeName(subordinateXid.getXID()), 1);
		}
	}

	@Test
	public void testForeignXID() {
		XidImple foreignXidImple = new XidImple(new MyForeignXID());

		assertEquals(XATxConverter.getNodeName(foreignXidImple.getXID()), -1);
		assertEquals(XATxConverter.getEISName(foreignXidImple.getXID()), "unknown eis name");
		assertEquals(XATxConverter.getSubordinateNodeName(foreignXidImple.getXID()), -1);
		assertEquals(XATxConverter.getParentNodeName(foreignXidImple.getXID()), -1);
	}

	private class MyForeignXID implements Xid {

		@Override
		public int getFormatId() {
			// TODO Auto-generated method stub
			return 1;
		}

		@Override
		public byte[] getGlobalTransactionId() {
			return "foo".getBytes();
		}

		@Override
		public byte[] getBranchQualifier() {
			return "bar".getBytes();
		}

	}
}
