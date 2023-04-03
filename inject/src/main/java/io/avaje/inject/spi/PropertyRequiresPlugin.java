package io.avaje.inject.spi;

import java.util.Optional;

/**
 * Plugin interface used with {@link io.avaje.inject.RequiresProperty}.
 * <p>
 * The plugin is loaded via ServiceLoader and defaults to an implementation
 * that uses {@link System#getProperty(String)} and {@link System#getenv(String)}.
 */
public interface PropertyRequiresPlugin {

  /** Return a configuration value that might not exist. */
  Optional<String> get(String property);

  /** Return true if the property is defined. */
  boolean contains(String property);

  /**
   * Return true if the property is not defined.
   */
  boolean missing(String property);

  /**
   * Return true if the property is equal to the given value.
   */
  boolean equalTo(String property, String value);

  /**
   * Return true if the property is not defined or not equal to the given value.
   */
  boolean notEqualTo(String property, String value);
}