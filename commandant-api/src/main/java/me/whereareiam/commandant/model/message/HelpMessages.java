package me.whereareiam.commandant.model.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Configuration model for help command message and formatting.
 * Used by HelpBuilder to format help command outputs.
 */
@Getter
@Setter
@ToString
public class HelpMessages {
	/**
	 * Overall format template for help display.
	 * Each string represents a line in the help output.
	 * Placeholders:
	 * - {commands}: List of available commands
	 * - {pagination}: Pagination controls
	 * <p>
	 * Example:
	 * - "<gradient:gold:yellow>Commands</gradient>"
	 * - ""
	 * - "{commands}"
	 * - ""
	 * - "{pagination}"
	 */
	private List<String> format;

	/**
	 * Format template for individual command entries.
	 * Placeholders:
	 * - {command}: Command name
	 * - {arguments}: Formatted command arguments
	 * - {description}: Command description
	 * <p>
	 * Example: "<gold>/{command}{arguments}</gold> <dark_gray>-</dark_gray> <gray>{description}</gray>"
	 */
	private String commandFormat;

	/**
	 * Message shown when no commands are available.
	 * <p>
	 * Example: "<red>No commands available</red>"
	 */
	private String noCommands;

	/**
	 * Format configuration for command arguments.
	 */
	private Format argumentFormat;

	/**
	 * Configuration for formatting command arguments.
	 */
	@Getter
	@Setter
	@ToString
	public static class Format {
		/**
		 * Format template for required arguments.
		 * Placeholder: {argument} - the argument name
		 * <p>
		 * Example: "<yellow><{argument}></yellow>"
		 */
		private String argument;

		/**
		 * Format template for optional arguments.
		 * Placeholder: {argument} - the argument name
		 * <p>
		 * Example: "<gray>[{argument}]</gray>"
		 */
		private String optionalArgument;
	}
}
