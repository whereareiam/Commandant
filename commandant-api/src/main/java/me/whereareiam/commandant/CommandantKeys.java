package me.whereareiam.commandant;

import org.incendo.cloud.key.CloudKey;

/**
 * Public constants for Commandant metadata keys.
 * <p>
 * These keys are used to attach metadata to Cloud commands and can be referenced
 * by platforms building custom annotation parsers.
 */
public final class CommandantKeys {
	/**
	 * Metadata key for storing the definition ID on a command.
	 * <p>
	 * This key is used to link commands with their configuration-based definitions.
	 * When a command has this metadata, {@link me.whereareiam.commandant.common.parsing.DefinitionParser}
	 * will apply definition overrides (aliases, permissions, descriptions, etc.).
	 * <p>
	 * Example usage in custom annotation parser:
	 * <pre>{@code
	 * cloudParser.registerBuilderModifier(
	 *     MyDefinition.class,
	 *     (annotation, builder) -> builder.meta(CommandantKeys.DEFINITION_ID, annotation.value())
	 * );
	 * }</pre>
	 */
	public static final CloudKey<String> DEFINITION_ID = CloudKey.of("commandant:definitionId", String.class);
}
