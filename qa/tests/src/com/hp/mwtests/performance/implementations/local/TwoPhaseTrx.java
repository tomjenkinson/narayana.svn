/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
 * (C) 2008,
 * @author JBoss Inc.
 */
package com.hp.mwtests.performance.implementations.local;

// Top-level transactions with two enlisted participants.
// (two phase commit/rollback)

import com.hp.mwtests.performance.PerformanceTest;
import com.hp.mwtests.performance.products.TxWrapper;
import com.hp.mwtests.performance.records.DummyResource;


public class TwoPhaseTrx extends PerformanceTest
{
    public void work()
    {
        try
        {
            TxWrapper tx = getTxWrapper();
            tx.begin(); // Top level begin

            // enlist two participants (resource) two-phase commit

            tx.add(DummyResource.create());
            tx.add(DummyResource.create());

            if (isParameterDefined("-commit"))
                tx.commit();  // Top level commit
            else
                tx.abort();  // Top level rollback

        }
        catch (Exception e)
        {
            System.err.println("Unexpected Exception: "+e);
            e.printStackTrace(System.err);
        }

    }
}
