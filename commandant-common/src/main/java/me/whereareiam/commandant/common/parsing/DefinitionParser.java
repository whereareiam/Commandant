package me.whereareiam.commandant.common.parsing;

import io.leangen.geantyref.TypeToken;
import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.adapter.DefinitionAdapter;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Applies definition overrides to Cloud commands.
 * <p>
 * Takes a parsed command and applies definition-based configuration
 * (aliases, descriptions, permissions, usage) to create the final command builders.
 *
 * @param <S> sender type
 * @param <D> definition type
 */
public final class DefinitionParser<S, D> {
	private final CommandManager<S> commandManager;
	private final DefinitionAdapter<D> adapter;

	public DefinitionParser(
			@NotNull CommandManager<S> commandManager,
			@NotNull DefinitionAdapter<D> adapter
	) {
		this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
		this.adapter = Objects.requireNonNull(adapter, "adapter");
	}

	/**
	 * Applies definition overrides to the parsed command and returns command builders
	 * for all aliases defined in the definition.
	 *
	 * @param definition     the command definition with overrides
	 * @param definitionId   the ID of the definition
	 * @param parsedCommand  the command parsed from annotations
	 * @param rootCommand    optional root command (e.g., "intercept" for "/intercept reload")
	 * @return list of command builders with definition overrides applied
	 */
	@NotNull
	public List<Command.Builder<S>> applyOverrides(
			@NotNull D definition,
			@NotNull String definitionId,
			@NotNull Command<S> parsedCommand,
			@Nullable String rootCommand
	) {
		if (!adapter.isEnabled(definition))
			return List.of();

		List<String> aliases = sanitizeAliases(adapter.getAliases(definition));
		if (aliases.isEmpty()) {
			throw new IllegalArgumentException("Command must define at least one alias");
		}

		// Extract variable components and usage tokens
		Map<String, CommandComponent<S>> variableComponents = extractVariableComponents(parsedCommand);
		List<ArgumentToken> usageTokens = parseUsage(adapter.getUsage(definition));

		// Group aliases by structure
		AliasStructure structure = analyzeAliases(aliases);

		// Build commands for each alias type
		List<Command.Builder<S>> builders = new ArrayList<>();
		builders.addAll(buildSingleWordAliases(
				definition, definitionId, parsedCommand, variableComponents,
				usageTokens, structure.singleWord(), rootCommand
		));
		builders.addAll(buildMultiWordAliases(
				definition, definitionId, parsedCommand, variableComponents,
				usageTokens, structure.multiWord(), rootCommand
		));

		return builders;
	}

	private List<Command.Builder<S>> buildSingleWordAliases(
			D definition,
			String definitionId,
			Command<S> parsedCommand,
			Map<String, CommandComponent<S>> variableComponents,
			List<ArgumentToken> usageTokens,
			List<String> aliases,
			@Nullable String rootCommand
	) {
		if (aliases.isEmpty()) return List.of();

		List<Command.Builder<S>> builders = new ArrayList<>();
		for (String alias : aliases) {
			List<String> aliasParts = splitAlias(alias);
			Command.Builder<S> builder;

			if (rootCommand != null) {
				builder = commandManager.commandBuilder(rootCommand);
				for (String part : aliasParts) {
					builder = builder.literal(part);
				}
			} else {
				String first = aliasParts.get(0);
				String[] rest = aliasParts.stream().skip(1).toArray(String[]::new);
				builder = commandManager.commandBuilder(first, rest);
			}

			builder = applyArguments(builder, usageTokens, variableComponents, definition);
			builder = applyMetadata(builder, definition, definitionId);
			builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));
			builders.add(builder);
		}
		return builders;
	}

	private List<Command.Builder<S>> buildMultiWordAliases(
			D definition,
			String definitionId,
			Command<S> parsedCommand,
			Map<String, CommandComponent<S>> variableComponents,
			List<ArgumentToken> usageTokens,
			List<String> aliases,
			@Nullable String rootCommand
	) {
		if (aliases.isEmpty()) return List.of();

		// Group by prefix
		Map<List<String>, List<String>> grouped = groupByPrefix(aliases);
		List<Command.Builder<S>> builders = new ArrayList<>();

		for (Map.Entry<List<String>, List<String>> entry : grouped.entrySet()) {
			List<String> prefix = entry.getKey();
			List<String> suffixes = distinct(entry.getValue());
			if (prefix.isEmpty() || suffixes.isEmpty()) continue;

			Command.Builder<S> builder;
			if (rootCommand != null) {
				builder = commandManager.commandBuilder(rootCommand);
				for (String part : prefix) {
					builder = builder.literal(part);
				}
			} else {
				String first = prefix.get(0);
				builder = commandManager.commandBuilder(first);
				for (int i = 1; i < prefix.size(); i++) {
					builder = builder.literal(prefix.get(i));
				}
			}

			String firstSuffix = suffixes.get(0);
			String[] restSuffixes = suffixes.stream().skip(1).toArray(String[]::new);
			builder = builder.literal(firstSuffix, restSuffixes);

			builder = applyArguments(builder, usageTokens, variableComponents, definition);
			builder = applyMetadata(builder, definition, definitionId);
			builder = builder.handler(ctx -> parsedCommand.commandExecutionHandler().executeFuture(ctx));
			builders.add(builder);
		}

		return builders;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Command.Builder<S> applyArguments(
			Command.Builder<S> builder,
			List<ArgumentToken> usageTokens,
			Map<String, CommandComponent<S>> variableComponents,
			D definition
	) {
		Map<String, String> argumentDescriptions = defaultMap(adapter.getArguments(definition));
		
		for (ArgumentToken token : usageTokens) {
			CommandComponent<S> component = variableComponents.get(token.name());
			if (component == null) {
				throw new IllegalArgumentException(
						"Definition usage token '%s' does not match any parsed command argument. Available arguments: %s"
								.formatted(token.name(), variableComponents.keySet())
				);
			}

			CommandComponent.Builder componentBuilder = createComponentBuilder(component, argumentDescriptions);
			
			if (token.required()) {
				builder = builder.required(componentBuilder);
			} else {
				builder = builder.optional(componentBuilder);
			}
		}
		return builder;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private CommandComponent.Builder<?, ?> createComponentBuilder(
			CommandComponent<S> component,
			Map<String, String> argumentDescriptions
	) {
		ArgumentParser rawParser = component.parser();
		TypeToken rawValueType = component.valueType();
		ParserDescriptor parserDescriptor = ParserDescriptor.of(rawParser, rawValueType);

		Description description = component.description();
		String customDesc = argumentDescriptions.get(component.name());
		if (customDesc != null && !customDesc.isBlank()) {
			description = Description.of(customDesc);
		}

		CommandComponent.Builder builder = CommandComponent.builder()
				.name(component.name())
				.parser(parserDescriptor)
				.description(description)
				.suggestionProvider(component.suggestionProvider());

		if (component.hasDefaultValue()) {
			builder = builder.defaultValue(component.defaultValue());
		}

		return builder;
	}

	private Command.Builder<S> applyMetadata(
			Command.Builder<S> builder,
			D definition,
			String definitionId
	) {
		builder = builder.meta(CommandantKeys.DEFINITION_ID, definitionId);

		String description = adapter.getDescription(definition);
		if (description != null && !description.isBlank()) {
			builder = builder.commandDescription(Description.of(description));
		}

		String permission = adapter.getPermission(definition);
		if (permission != null && !permission.isBlank()) {
			builder = builder.permission(Permission.of(permission));
		}

		return builder;
	}

	private Map<String, CommandComponent<S>> extractVariableComponents(Command<S> parsedCommand) {
		Map<String, CommandComponent<S>> map = new HashMap<>();
		for (CommandComponent<S> component : parsedCommand.components()) {
			if (component.type() != CommandComponent.ComponentType.LITERAL) {
				map.put(component.name(), component);
			}
		}
		return map;
	}

	private List<ArgumentToken> parseUsage(@Nullable String usage) {
		if (usage == null || usage.isBlank()) {
			return Collections.emptyList();
		}

		List<ArgumentToken> tokens = new ArrayList<>();
		for (String token : usage.split("\\s+")) {
			if (token.isBlank()) continue;
			if (token.startsWith("{") && token.endsWith("}")) continue;

			boolean required = token.startsWith("<") && token.endsWith(">");
			boolean optional = token.startsWith("[") && token.endsWith("]");
			if (!required && !optional) continue;

			String cleaned = token.substring(1, token.length() - 1).trim();
			boolean greedy = cleaned.endsWith("...");
			if (greedy) {
				cleaned = cleaned.substring(0, cleaned.length() - 3);
			}

			if (!cleaned.isEmpty()) {
				tokens.add(new ArgumentToken(cleaned, required, greedy));
			}
		}

		return tokens;
	}

	private List<String> sanitizeAliases(@Nullable List<String> aliases) {
		if (aliases == null || aliases.isEmpty()) {
			return Collections.emptyList();
		}

		LinkedHashSet<String> sanitized = new LinkedHashSet<>();
		for (String alias : aliases) {
			if (alias == null) continue;
			String trimmed = alias.trim();
			if (!trimmed.isEmpty()) {
				sanitized.add(trimmed);
			}
		}

		return new ArrayList<>(sanitized);
	}

	private AliasStructure analyzeAliases(List<String> aliases) {
		List<String> singleWord = new ArrayList<>();
		List<String> multiWord = new ArrayList<>();

		for (String alias : aliases) {
			if (alias.contains(" ")) {
				multiWord.add(alias);
			} else {
				singleWord.add(alias);
			}
		}

		return new AliasStructure(singleWord, multiWord);
	}

	private Map<List<String>, List<String>> groupByPrefix(List<String> multiWordAliases) {
		Map<List<String>, List<String>> grouped = new LinkedHashMap<>();

		for (String alias : multiWordAliases) {
			List<String> parts = splitAlias(alias);
			if (parts.size() < 2) continue;

			List<String> prefix = List.copyOf(parts.subList(0, parts.size() - 1));
			String suffix = parts.get(parts.size() - 1);
			if (suffix.isBlank()) continue;

			grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(suffix);
		}

		return grouped;
	}

	private List<String> distinct(List<String> in) {
		if (in.isEmpty()) return in;
		return new ArrayList<>(new LinkedHashSet<>(in));
	}

	private List<String> splitAlias(String alias) {
		return List.of(alias.trim().split("\\s+"));
	}

	private Map<String, String> defaultMap(Map<String, String> map) {
		return map == null ? Collections.emptyMap() : map;
	}

	public record ArgumentToken(String name, boolean required, boolean greedy) {}
	private record AliasStructure(List<String> singleWord, List<String> multiWord) {}
}
