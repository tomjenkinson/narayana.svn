package com.arjuna.ats.internal.jta.resources.jta;

import com.arjuna.ats.internal.jta.transaction.jts.TransactionImple;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;
import com.arjuna.ats.internal.jta.xa.TxInfo;
import com.arjuna.ats.jta.logging.jtaLogger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class XAResourceRecord extends
        com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord  {

    private TransactionImple _tx;

    public XAResourceRecord(TransactionImple tx, XAResource res, Xid xid,
			Object[] params) {
        super(null, res, xid, params);

    }

	public int topLevelAbort()
	{
		if (jtaLogger.logger.isTraceEnabled()) {
            jtaLogger.logger.trace("XAResourceRecord.topLevelAbort for " + this);
        }

		if (_tx != null
				&& _tx.getXAResourceState(_theXAResource) == TxInfo.OPTIMIZED_ROLLBACK)
		{
			/*
			 * Already rolledback during delist.
			 */

			return TwoPhaseOutcome.FINISH_OK;
		} else {
            return super.topLevelAbort();
        }
    }

 	protected boolean endAssociation()
	{
		boolean doEnd = true;

		if (_tx != null)
		{
			if (_tx.getXAResourceState(_theXAResource) == TxInfo.NOT_ASSOCIATED)
			{
				// end has been called so we don't need to do it again!

				doEnd = false;
			}
		}
		else
			doEnd = false; // Recovery mode

		return doEnd;
	}
}
