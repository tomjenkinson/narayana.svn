/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. 
 * See the copyright.txt in the distribution for a full listing 
 * of individual contributors.
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
package com.arjuna.webservices11.wsarjtx.processors;

import com.arjuna.schemas.ws._2005._10.wsarjtx.NotificationType;
import com.arjuna.webservices.SoapFault;
import com.arjuna.webservices.base.processors.Callback;
import com.arjuna.webservices11.wsarj.ArjunaContext;
import org.jboss.ws.api.addressing.MAP;

/**
 * The Terminator Coordinator callback.
 * @author kevin
 */
public abstract class TerminationParticipantCallback extends Callback
{
    /**
     * A cancelled response.
     * @param cancelled The cancelled notification.
     * @param map The addressing context.
     * @param arjunaContext The arjuna context.
     */
    public abstract void cancelled(final NotificationType cancelled, final MAP map,
        final ArjunaContext arjunaContext) ;

    /**
     * A closed response.
     * @param closed The closed notification.
     * @param map The addressing context.
     * @param arjunaContext The arjuna context.
     */
    public abstract void closed(final NotificationType closed, final MAP map,
        final ArjunaContext arjunaContext) ;

    /**
     * A completed response.
     * @param completed The completed notification.
     * @param map The addressing context.
     * @param arjunaContext The arjuna context.
     */
    public abstract void completed(final NotificationType completed, final MAP map,
        final ArjunaContext arjunaContext) ;

    /**
     * A faulted response.
     * @param faulted The faulted notification.
     * @param map The addressing context.
     * @param arjunaContext The arjuna context.
     */
    public abstract void faulted(final NotificationType faulted, final MAP map,
        final ArjunaContext arjunaContext) ;

    /**
     * A SOAP fault response.
     * @param soapFault The SOAP fault.
     * @param map The addressing context.
     * @param arjunaContext The arjuna context.
     */
    public abstract void soapFault(final SoapFault soapFault, final MAP map,
        final ArjunaContext arjunaContext) ;
}