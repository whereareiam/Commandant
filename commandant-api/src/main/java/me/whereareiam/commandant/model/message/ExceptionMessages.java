package me.whereareiam.commandant.model.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Configuration model for exception-related message.
 */
@Getter
@Setter
@ToString
public class ExceptionMessages {
	/**
	 * Message shown when a player lacks permission to execute a command.
	 * Placeholder: {content} - the missing permission string
	 */
	private String noPermission;

	/**
	 * Message shown when a command execution encounters an error.
	 * Placeholder: {content} - the error message
	 */
	private String executionError;

	/**
	 * Message shown for invalid command syntax.
	 * Placeholder: {content} - the correct syntax
	 */
	private String invalidSyntax;

	/**
	 * Message shown when a boolean argument is invalid.
	 * Placeholder: {content} - the invalid input
	 */
	private String invalidSyntaxBoolean;

	/**
	 * Message shown when a numeric argument is invalid.
	 * Placeholder: {content} - the invalid input
	 */
	private String invalidSyntaxNumber;

	/**
	 * Message shown when a string argument is invalid.
	 * Placeholder: {content} - the invalid input
	 */
	private String invalidSyntaxString;

	/**
	 * Message shown when an invalid command sender attempts to execute a command.
	 * (e.g., console trying to execute a player-only command)
	 */
	private String invalidSender;
}