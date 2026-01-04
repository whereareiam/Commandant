package me.whereareiam.commandant;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for exception message keys used in translation systems.
 * Allows users to map Commandant's internal exception types to their own key structure.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Use custom key structure
 * ExceptionMessageKeys keys = ExceptionMessageKeys.builder()
 *     .noPermission("my.plugin.errors.no_perm")
 *     .invalidSyntax("my.plugin.errors.syntax")
 *     .build();
 *
 * // Or use defaults
 * ExceptionMessageKeys keys = ExceptionMessageKeys.defaults();
 * }</pre>
 */
@Getter
@Builder
@SuppressWarnings("unused")
public class ExceptionMessageKeys {
	/**
	 * Key for no permission messages.
	 * Placeholder: content - the missing permission string
	 */
	@NotNull
	private final String noPermission;

	/**
	 * Key for command execution error messages.
	 * Placeholder: content - the error message
	 */
	@NotNull
	private final String executionError;

	/**
	 * Key for invalid command syntax messages.
	 * Placeholder: content - the correct syntax
	 */
	@NotNull
	private final String invalidSyntax;

	/**
	 * Key for invalid boolean argument messages.
	 * Placeholder: content - the invalid input
	 */
	@NotNull
	private final String invalidSyntaxBoolean;

	/**
	 * Key for invalid numeric argument messages.
	 * Placeholder: content - the invalid input
	 */
	@NotNull
	private final String invalidSyntaxNumber;

	/**
	 * Key for invalid string argument messages.
	 * Placeholder: content - the invalid input
	 */
	@NotNull
	private final String invalidSyntaxString;

	/**
	 * Key for invalid command sender messages.
	 * (e.g., console trying to execute a player-only command)
	 */
	@NotNull
	private final String invalidSender;

	/**
	 * Creates a default key configuration using "commandant.exception.*" prefix.
	 *
	 * @return default exception message keys
	 */
	@NotNull
	public static ExceptionMessageKeys defaults() {
		return builder()
				.noPermission("commandant.exception.no_permission")
				.executionError("commandant.exception.execution_error")
				.invalidSyntax("commandant.exception.invalid_syntax")
				.invalidSyntaxBoolean("commandant.exception.invalid_syntax.boolean")
				.invalidSyntaxNumber("commandant.exception.invalid_syntax.number")
				.invalidSyntaxString("commandant.exception.invalid_syntax.string")
				.invalidSender("commandant.exception.invalid_sender")
				.build();
	}
}
