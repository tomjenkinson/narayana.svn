/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, JBoss Inc., and others contributors as indicated 
 * by the @authors tag. All rights reserved. 
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
package com.hp.mwtests.ts.arjuna.objectstore;

/*
 * Copyright (C) 2001,
 *
 * Hewlett-Packard Arjuna Labs,
 * Newcastle upon Tyne,
 * Tyne and Wear,
 * UK.
 *
 * $Id: MyAccess.java 2342 2006-03-30 13:06:17Z  $
 */

import com.arjuna.ats.arjuna.gandiva.ObjectName;

import java.sql.*;

public class MyAccess implements com.arjuna.ats.arjuna.objectstore.jdbc.JDBCAccess
{

    public Connection getConnection () throws SQLException
    {
	return null;
    }
    
    public void putConnection (Connection conn)
    {
    }

    public String tableName ()
    {
	return null;
    }

    public boolean dropTable ()
    {
	return false;
    }

    public void initialise (ObjectName objName)
    {
    }
    
}
