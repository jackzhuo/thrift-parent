package com.github.thrift.consumer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

public class ThriftRefAnnBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware
{
	private static final Logger logger = LoggerFactory.getLogger(ThriftRefAnnBeanPostProcessor.class);

	private ApplicationContext applicationContext;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
	{
		this.applicationContext = applicationContext;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException
	{
		Method[] methods = bean.getClass().getMethods();
		for (Method method : methods)
		{
			String name = method.getName();
			if (name.length() > 3 && name.startsWith("set") && method.getParameterTypes().length == 1 && Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
			{
				try
				{
					ThriftReference reference = method.getAnnotation(ThriftReference.class);
					if (reference != null)
					{
						Object value = refer(reference, method.getParameterTypes()[0]);
						if (value != null)
						{
							method.invoke(bean, new Object[] {});
						}
					}
				} catch (Throwable e)
				{
					logger.error("Failed to init remote service reference at method " + name + " in class " + bean.getClass().getName() + ", cause: " + e.getMessage(), e);
				}
			}
		}
		Field[] fields = bean.getClass().getDeclaredFields();
		for (Field field : fields)
		{
			try
			{
				if (!field.isAccessible())
				{
					field.setAccessible(true);
				}
				ThriftReference reference = field.getAnnotation(ThriftReference.class);
				if (reference != null)
				{
					Object value = refer(reference, field.getType());
					if (value != null)
					{
						field.set(bean, value);
					}
				}
			} catch (Throwable e)
			{
				logger.error("Failed to init remote service reference at filed " + field.getName() + " in class " + bean.getClass().getName() + ", cause: " + e.getMessage(), e);
			}
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException
	{
		return bean;
	}

	private Object refer(ThriftReference reference, Class<?> referenceClass)
	{
		ThriftServerDiscovery discovery = applicationContext.getBean(ThriftServerDiscovery.class);
		ThriftConsumerProxy proxy = applicationContext.getBean(ThriftConsumerProxy.class);
		String version = reference.version();
		Class<?> serviceClass = referenceClass.getDeclaringClass();
		String address = discovery.getAddress(serviceClass.getName(), version);
		if(StringUtils.isEmpty(address) || address.split(":").length <2)
		{
			return null;
		}
		String[] str = address.split(":");
		String host = str[0];
		int port = Integer.valueOf(str[1]);
		return proxy.proxy(referenceClass, host, port);
	}

}
