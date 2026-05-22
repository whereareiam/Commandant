package me.whereareiam.commandant.exception.format;

import org.jetbrains.annotations.NotNull;

/**
 * Formats a value of a specific type for display in user-facing messages.
 *
 * @param <T> value type
 */
public interface ExceptionFormatter<T> {
	/**
	 * Formats the given value for display.
	 *
	 * @param value value to format
	 * @return formatted display text
	 */
	@NotNull
	String format(@NotNull T value);
}
