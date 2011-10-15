package com.arjuna.ats.jta.distributed.server;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class IsolatableServersClassLoader extends ClassLoader {

	private Map<String, Class<?>> clazzMap = new HashMap<String, Class<?>>();
	private Method m;

	public IsolatableServersClassLoader(ClassLoader parent) throws SecurityException, NoSuchMethodException {
		super(parent);
		m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
		m.setAccessible(true);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (clazzMap.containsKey(name)) {
			return clazzMap.get(name);
		}
		return super.findClass(name);
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		Class<?> clazz = null;
		if (clazzMap.containsKey(name)) {
			clazz = clazzMap.get(name);
		}

		try {
			ClassLoader parent2 = getParent();
			Object test1 = m.invoke(parent2, name);
			if (test1 != null) {
				
				if (!name.equals("")) {
					clazz = super.loadClass(name);
				}
			} else {
				try {
					String url = "file:" + System.getProperty("user.dir") + "/bin/" + name.replace('.', '/') + ".class";
					URL myUrl = new URL(url);
					try {
						URLConnection connection = myUrl.openConnection();
						InputStream input = connection.getInputStream();
						ByteArrayOutputStream buffer = new ByteArrayOutputStream();
						int data = input.read();

						while (data != -1) {
							buffer.write(data);
							data = input.read();
						}

						input.close();

						byte[] classData = buffer.toByteArray();

						clazz = defineClass(name, classData, 0, classData.length);
						clazzMap.put(name, clazz);
					} catch (FileNotFoundException fnfe) {
						return super.loadClass(name);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (IllegalArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InvocationTargetException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return clazz;
	}
}