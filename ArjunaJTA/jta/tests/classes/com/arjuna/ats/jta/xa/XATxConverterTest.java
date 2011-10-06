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
			assertEquals(XATxConverter.getEISName(rootXid.getXID()), "foo");
			assertEquals(
					XATxConverter.getSubordinateNodeName(rootXid.getXID()), 1);
			assertEquals(XATxConverter.getSubordinateParentNodeName(rootXid
					.getXID()), 0);
		}

		TxControl.setXANodeName(2);
		XidImple subordinateXid = new XidImple(rootXid, true);
		{
			assertEquals(XATxConverter.getNodeName(subordinateXid.getXID()), 1);
			assertEquals(XATxConverter.getEISName(subordinateXid.getXID()),
					"foo");
			assertEquals(XATxConverter.getSubordinateNodeName(subordinateXid
					.getXID()), 2);
			assertEquals(
					XATxConverter.getSubordinateParentNodeName(subordinateXid
							.getXID()), 1);
		}
	}

	public void testForeignXID() {
		XidImple foreignXidImple = new XidImple(new MyForeignXID());

		assertEquals(XATxConverter.getNodeName(foreignXidImple.getXID()), -1);
		assertEquals(XATxConverter.getEISName(foreignXidImple.getXID()), null);
		assertEquals(
				XATxConverter.getSubordinateNodeName(foreignXidImple.getXID()),
				0);
		assertEquals(XATxConverter.getSubordinateParentNodeName(foreignXidImple
				.getXID()), 0);

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
