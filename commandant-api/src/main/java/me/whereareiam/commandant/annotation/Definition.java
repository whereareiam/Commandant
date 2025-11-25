package me.whereareiam.commandant.annotation;

import me.whereareiam.commandant.model.CommandDefinition;

import java.lang.annotation.*;

/**
 * Associates a Cloud-annotated command container or handler method with a
 * {@link CommandDefinition Definition} entry.
 * <p>
 * The {@link #value()} should match the key inside the configuration map (e.g., {@code "help"},
 * {@code "reload"}). When the annotation is present on both the class and the method, the method-level
 * value takes precedence.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Definition {
	/**
	 * Identifier of the definition inside the configuration.
	 *
	 * @return definition identifier (e.g., {@code "main"})
	 */
	String value();
}