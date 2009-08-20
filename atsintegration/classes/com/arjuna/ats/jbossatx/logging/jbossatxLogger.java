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
/*
 * Copyright (C) 2001, 2002,
 *
 * Hewlett-Packard Arjuna Labs,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: jbossatxLogger.java $
 */

package com.arjuna.ats.jbossatx.logging;

import com.arjuna.common.util.logging.*;

import com.arjuna.ats.jta.common.jtaPropertyManager;

import java.util.Locale;
import java.util.ResourceBundle;

public class jbossatxLogger
{
    public static LogNoi18n      logger;
    public static Logi18n        loggerI18N;
    public static ResourceBundle logMesg;

    private static String _language;
    private static String _country;
    private static Locale _currentLocale;

    static
    {
        logger = LogFactory.getLogNoi18n("com.arjuna.ats.jbossatx.logging.logger");

        _language = System.getProperty("language","en");
        _country = System.getProperty("country","US");

        _currentLocale = new Locale(_language, _country);
        
        try
        {
            logMesg = ResourceBundle.getBundle("jbossatx_msg", _currentLocale);
        }
        catch (Throwable ex)
        {
            logMesg = null;
        }

        if (logMesg == null)
        {
            _currentLocale = new Locale("en", "US");
            
            logMesg = ResourceBundle.getBundle("jbossatx_msg", _currentLocale);
        }
        
        try
        {
            loggerI18N = LogFactory.getLogi18n("com.arjuna.ats.jbossatx.logging.loggerI18N", "jbossatx_msg_"+_language+"_"+_country);
        }
        catch (Throwable ex)
        {
            loggerI18N = null;
        }
        
        if (loggerI18N == null)
            loggerI18N = LogFactory.getLogi18n("com.arjuna.ats.jbossatx.logging.loggerI18N", "jbossatx_msg_en_US");
    }
}



