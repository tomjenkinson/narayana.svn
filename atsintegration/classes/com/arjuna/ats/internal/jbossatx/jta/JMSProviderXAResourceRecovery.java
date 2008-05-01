/*
* JBoss, Home of Professional Open Source
* Copyright 2006 to 2008, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package com.arjuna.ats.internal.jbossatx.jta;

import javax.transaction.xa.XAResource;

import org.jboss.jms.recovery.XAResourceWrapper;

import com.arjuna.ats.jta.recovery.XAResourceRecovery;

import org.jboss.logging.Logger;

/**
 * This class provides recovery for JMS resources.

 *
 * To use this class, add an XAResourceRecovery entry in the jta section of jbossjta-properties.xml
 * for JMS provider for which you need recovery, ensuring the value ends with ;<provider-name>
 * i.e. the same value as is in the -ds.xml ProviderName element.
 * You also need the XARecoveryModule enabled and appropriate values for nodeIdentifier and xaRecoveryNode set.
 * See the JBossTS recovery guide if you are unclear on how the recovery system works.
 *
 * Note: This implementation expects to run inside the app server JVM.
 *
 *  <properties depends="arjuna" name="jta">
 *  ...
 *    <property name="com.arjuna.ats.jta.recovery.XAResourceRecovery.JBMESSAGING1"
 *         value="com.arjuna.ats.internal.jbossatx.jta.JMSProviderXAResourceRecovery;java:/DefaultJMSProvider"/>
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 */
public class JMSProviderXAResourceRecovery implements XAResourceRecovery
{
    private Logger log = org.jboss.logging.Logger.getLogger(JMSProviderXAResourceRecovery.class);

    /** The jms provider name */
    private String providerName;

    /** The delegate XAResource */
    private XAResourceWrapper wrapper;

    /** Whether the XAResource is working */
    private boolean working = false;

    public boolean initialise(String p)
    {
        this.providerName = p;

        if (log.isDebugEnabled())
            log.debug("initialise: using provider " + p);

        return true;
    }

    public boolean hasMoreResources()
    {
        if (working)
        {
            working = false; // reset ready for the next recovery scan

            return false;
        }

        // Have we initialized yet?
        if (wrapper == null)
        {
            wrapper = new XAResourceWrapper();
            wrapper.setProviderName(providerName);
        }

        // Test the connection
        try
        {
            wrapper.getTransactionTimeout();
            working = true;
        }
        catch (Exception ignored)
        {
            if (log.isDebugEnabled())
                log.debug("Still waiting for provider " + providerName);
        }

        // This will return false until we get
        // a successful connection
        return working;
    }

    public XAResource getXAResource()
    {
        return wrapper;
    }
}
