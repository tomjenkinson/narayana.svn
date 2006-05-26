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
package com.arjuna.webservices.wsarjtx.policy;

import com.arjuna.webservices.HandlerRegistry;
import com.arjuna.webservices.base.handlers.LoggingFaultHandler;
import com.arjuna.webservices.wsarjtx.ArjunaTXConstants;
import com.arjuna.webservices.wsarjtx.handlers.TerminationCoordinatorCancelHandler;
import com.arjuna.webservices.wsarjtx.handlers.TerminationCoordinatorCloseHandler;
import com.arjuna.webservices.wsarjtx.handlers.TerminationCoordinatorCompleteHandler;

/**
 * Policy responsible for binding in the terminator participant body handlers.
 * @author kevin
 */
public class TerminationCoordinatorPolicy
{
    /**
     * Add this policy to the registry.
     * @param registry The registry containing the policy.
     */
    public static void register(final HandlerRegistry registry)
    {
        registry.registerBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_CLOSE_QNAME, new TerminationCoordinatorCloseHandler()) ;
        registry.registerBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_CANCEL_QNAME, new TerminationCoordinatorCancelHandler()) ;
        registry.registerBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_COMPLETE_QNAME, new TerminationCoordinatorCompleteHandler()) ;
        registry.registerFaultHandler(new LoggingFaultHandler(ArjunaTXConstants.SERVICE_TERMINATION_COORDINATOR)) ;
    }

    /**
     * Remove this policy from the registry.
     * @param registry The registry containing the policy.
     */
    public static void remove(final HandlerRegistry registry)
    {
        registry.registerFaultHandler(null) ;
        registry.removeBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_COMPLETE_QNAME) ;
        registry.removeBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_CANCEL_QNAME) ;
        registry.removeBodyHandler(ArjunaTXConstants.WSARJTX_ELEMENT_CLOSE_QNAME) ;
    }
}
