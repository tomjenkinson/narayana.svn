/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.  All rights reserved. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 * 
 * (C) 2005-2006,
 * @author JBoss Inc.
 */
/*
 * Copyright (C) 2002,
 *
 * Arjuna Technologies Limited,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: SuspendParticipant.java,v 1.2 2005/01/15 21:21:03 kconner Exp $
 */

package com.arjuna.mwtests.wscf.model.twophase;

import com.arjuna.mwtests.wscf.common.*;

import com.arjuna.mw.wscf.model.twophase.api.UserCoordinator;
import com.arjuna.mw.wscf.model.twophase.api.CoordinatorManager;

import com.arjuna.mw.wscf.model.twophase.UserCoordinatorFactory;
import com.arjuna.mw.wscf.model.twophase.CoordinatorManagerFactory;

import com.arjuna.mwlabs.wscf.utils.ProtocolLocator;

import com.arjuna.mw.wscf.model.twophase.common.*;
import com.arjuna.mw.wscf.model.twophase.exceptions.*;

import com.arjuna.mw.wscf.common.*;

import com.arjuna.mw.wsas.activity.*;

import com.arjuna.mw.wsas.exceptions.NoActivityException;

import com.arjuna.mw.wscf.exceptions.*;

/**
 * @author Mark Little (mark.little@arjuna.com)
 * @version $Id: SuspendParticipant.java,v 1.2 2005/01/15 21:21:03 kconner Exp $
 * @since 1.0.
 */

public class SuspendParticipant
{

    public static void main (String[] args)
    {
	boolean passed = false;
	
	try
	{
	    CoordinatorManager cm = CoordinatorManagerFactory.coordinatorManager();
	    
	    cm.begin();

	    cm.enlistParticipant(new TwoPhaseParticipant(null));
	    cm.enlistParticipant(new TwoPhaseParticipant(null));
	    cm.enlistSynchronization(new TwoPhaseSynchronization());
	    
	    System.out.println("Started: "+cm.identifier()+"\n");

	    ActivityHierarchy hier = cm.suspend();

	    System.out.println("Suspended: "+hier+"\n");

	    if (cm.currentActivity() != null)
	    {
		System.out.println("Hierarchy still active.");

		cm.cancel();
	    }
	    else
	    {
		System.out.println("Resumed: "+hier+"\n");
		
		cm.resume(hier);
		
		cm.confirm();

		passed = true;
	    }
	}
	catch (Exception ex)
	{
	    ex.printStackTrace();

	    passed = false;
	}
	
	if (passed)
	    System.out.println("\nPassed.");
	else
	    System.out.println("\nFailed.");
    }

}
