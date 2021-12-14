package io.avaje.inject.spi;

import io.avaje.inject.BeanEntry;
import io.avaje.inject.BeanScope;
import io.avaje.inject.Priority;
import io.avaje.lang.NonNullApi;
import io.avaje.lang.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@NonNullApi
class DBeanScope implements BeanScope {

  private final ReentrantLock lock = new ReentrantLock();
  private final List<Runnable> postConstruct;
  private final List<AutoCloseable> preDestroy;
  private final DBeanMap beans;
  private final ShutdownHook shutdownHook;
  private final BeanScope parent;
  private boolean shutdown;
  private boolean closed;

  DBeanScope(boolean withShutdownHook, List<AutoCloseable> preDestroy, List<Runnable> postConstruct, DBeanMap beans, BeanScope parent) {
    this.preDestroy = preDestroy;
    this.postConstruct = postConstruct;
    this.beans = beans;
    this.parent = parent;
    if (withShutdownHook) {
      this.shutdownHook = new ShutdownHook(this);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    } else {
      this.shutdownHook = null;
    }
  }

  @Override
  public String toString() {
    return "BeanScope{" + beans + '}';
  }

  @Override
  public List<BeanEntry> all() {
    IdentityHashMap<DContextEntryBean, DEntry> map = new IdentityHashMap<>();
    if (parent != null) {
      ((DBeanScope) parent).addAll(map);
    }
    addAll(map);
    return new ArrayList<>(map.values());
  }

  void addAll(Map<DContextEntryBean, DEntry> map) {
    beans.addAll(map);
  }

  @Override
  public <T> T get(Class<T> type) {
    return getByType(type, null);
  }

  @Override
  public <T> T get(Class<T> type, @Nullable String name) {
    return getByType(type, name);
  }

  @Override
  public <T> T get(Type type, @Nullable String name) {
    return getByType(type, name);
  }

  private  <T> T getByType(Type type, @Nullable String name) {
    final T bean = beans.get(type, name);
    if (bean != null) {
      return bean;
    }
    if (parent == null) {
      throw new NoSuchElementException("No bean found for type: " + type + " name: " + name);
    }
    return parent.get(type, name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> List<T> list(Class<T> interfaceType) {
    List<T> values = (List<T>) beans.all(interfaceType);
    if (parent == null) {
      return values;
    }
    return combine(values, parent.list(interfaceType));
  }

  static <T> List<T> combine(List<T> values, List<T> parentValues) {
    if (values.isEmpty()) {
      return parentValues;
    } else if (parentValues.isEmpty()) {
      return values;
    } else {
      values.addAll(parentValues);
      return values;
    }
  }

  @Override
  public <T> List<T> listByPriority(Class<T> interfaceType) {
    return listByPriority(interfaceType, Priority.class);
  }

  @Override
  public <T> List<T> listByPriority(Class<T> interfaceType, Class<? extends Annotation> priorityAnnotation) {
    List<T> list = list(interfaceType);
    return list.size() > 1 ? sortByPriority(list, priorityAnnotation) : list;
  }

  private <T> List<T> sortByPriority(List<T> list, final Class<? extends Annotation> priorityAnnotation) {
    boolean priorityUsed = false;
    List<SortBean<T>> tempList = new ArrayList<>(list.size());
    for (T bean : list) {
      SortBean<T> sortBean = new SortBean<>(bean, priorityAnnotation);
      tempList.add(sortBean);
      if (!priorityUsed && sortBean.priorityDefined) {
        priorityUsed = true;
      }
    }
    if (!priorityUsed) {
      // nothing with Priority annotation so return original order
      return list;
    }
    Collections.sort(tempList);
    // unpack into new sorted list
    List<T> sorted = new ArrayList<>(tempList.size());
    for (SortBean<T> sortBean : tempList) {
      sorted.add(sortBean.bean);
    }
    return sorted;
  }

  @Override
  public List<Object> listByAnnotation(Class<?> annotation) {
    final List<Object> values = beans.all(annotation);
    if (parent == null) {
      return values;
    }
    return combine(values, parent.listByAnnotation(annotation));
  }

  DBeanScope start() {
    lock.lock();
    try {
      Log.trace("firing postConstruct");
      for (Runnable invoke : postConstruct) {
        invoke.run();
      }
    } finally {
      lock.unlock();
    }
    return this;
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (shutdownHook != null && !shutdown) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      }
      if (!closed) {
        // we only allow one call to preDestroy
        closed = true;
        Log.trace("firing preDestroy");
        for (AutoCloseable closeable : preDestroy) {
          try {
            closeable.close();
          } catch (Exception e) {
            Log.error("Error during PreDestroy lifecycle method", e);
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void shutdown() {
    lock.lock();
    try {
      shutdown = true;
      close();
    } finally {
      lock.unlock();
    }
  }

  private static class ShutdownHook extends Thread {
    private final DBeanScope scope;

    ShutdownHook(DBeanScope scope) {
      this.scope = scope;
    }

    @Override
    public void run() {
      scope.shutdown();
    }
  }

  private static class SortBean<T> implements Comparable<SortBean<T>> {

    private final T bean;

    private boolean priorityDefined;

    private final int priority;

    SortBean(T bean, Class<? extends Annotation> priorityAnnotation) {
      this.bean = bean;
      this.priority = initPriority(priorityAnnotation);
    }

    int initPriority(Class<? extends Annotation> priorityAnnotation) {
      // Avoid adding hard dependency on javax.annotation-api by using reflection
      try {
        Annotation ann = bean.getClass().getDeclaredAnnotation(priorityAnnotation);
        if (ann != null) {
          int priority = (Integer) priorityAnnotation.getMethod("value").invoke(ann);
          priorityDefined = true;
          return priority;
        }
      } catch (Exception e) {
        // If this happens, something has gone very wrong since a non-confirming @Priority was found...
        throw new UnsupportedOperationException("Problem instantiating @Priority", e);
      }
      // Default priority as per javax.ws.rs.Priorities.USER
      // User-level filter/interceptor priority
      return 5000;
    }

    @Override
    public int compareTo(SortBean<T> o) {
      return Integer.compare(priority, o.priority);
    }
  }
}
