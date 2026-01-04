package me.whereareiam.commandant.annotation;

import java.lang.annotation.*;

/**
 * Associates a Cloud-annotated command container or handler method with a
 * definition entry provided by the platform.
 * <p>
 * The {@link #value()} should match the key in the platform's definition lookup
 * (e.g., {@code "help"}, {@code "reload"}). When the annotation is present on both
 * the class and the method, the method-level value takes precedence.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Definition {
	/**
	 * Identifier of the definition in the platform's configuration.
	 *
	 * @return definition identifier (e.g., {@code "main"}, {@code "help"})
	 */
	String value();
}