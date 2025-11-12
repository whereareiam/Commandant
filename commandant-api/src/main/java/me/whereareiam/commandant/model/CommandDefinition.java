package me.whereareiam.commandant.model;

import lombok.*;

import java.util.List;

/**
 * Model representing a command definition.
 * Contains all the properties needed to register a command.
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CommandDefinition {
	@Builder.Default
	private boolean enabled = true;
	private List<String> aliases;

	@Builder.Default
	private String permission = "";
	private String description;
	private String usage;

	private Cooldown cooldown;

	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder(toBuilder = true)
	public static class Cooldown {
		@Builder.Default
		private boolean enabled = false;
		@Builder.Default
		private int duration = 0;
		@Builder.Default
		private String group = "default";
	}
}

