package me.whereareiam.commandant;

import me.whereareiam.commandant.adapter.DefinitionAdapter;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Commandant}'s process API.
 * <p>
 * Note: These tests focus on definition processing, not annotation parsing.
 * Annotation parsing is the responsibility of each framework (e.g., OraylenAnnotationParser).
 */
class CommandantTest {
	private CommandManager<TestSender> commandManager;

	@BeforeEach
	void setUp() {
		this.commandManager = new CommandManager<>(
				ExecutionCoordinator.simpleCoordinator(),
				CommandRegistrationHandler.nullCommandRegistrationHandler()
		) {
			@Override
			public boolean hasPermission(@NotNull TestSender sender, @NotNull String permission) {
				return true;
			}
		};
	}

	@Test
	void testProcessCommandWithoutDefinition() {
		// Create a simple command without definition metadata
		Command<TestSender> command = commandManager.commandBuilder("test")
				.handler(ctx -> {})
				.build();

		// Process without definition
		Commandant.process(command, commandManager)
				.withoutDefinition();

		// Command should be registered
		assertEquals(1, commandManager.commands().size());
	}

	@Test
	void testProcessCommandWithNullDefinition() {
		// Create command with definition ID metadata but no actual definition
		Command<TestSender> command = commandManager.commandBuilder("test")
				.meta(CommandantKeys.DEFINITION_ID, "test-def")
				.handler(ctx -> {})
				.build();

		// Process with null definition (definition not found)
		Commandant.process(command, commandManager)
				.withDefinition(null, new TestAdapter())
				.register();

		// Command should be registered as-is
		assertEquals(1, commandManager.commands().size());
	}

	@Test
	void testProcessCommandWithDefinition() {
		// Create definition with aliases
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("test", "t"))
				.description("Test command")
				.permission("test.use")
				.build();

		// Create command with definition ID
		Command<TestSender> command = commandManager.commandBuilder("original")
				.meta(CommandantKeys.DEFINITION_ID, "test-def")
				.handler(ctx -> {})
				.build();

		// Process with definition
		List<Command.Builder<TestSender>> builders = Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter())
				.builders();

		// Should create builders for each alias
		assertEquals(2, builders.size());
	}

	@Test
	void testProcessCommandWithRootAliasesBuildsSingleRootCommand() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("identica", "auth"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("original")
				.meta(CommandantKeys.DEFINITION_ID, "main")
				.handler(ctx -> {})
				.build();

		List<Command<TestSender>> built = Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter(), List.of("identica", "auth"))
				.build();

		assertEquals(1, built.size());
		assertEquals("identica", built.get(0).rootComponent().name());
		assertEquals(List.of("auth"), new ArrayList<>(built.get(0).rootComponent().alternativeAliases()));
	}

	@Test
	void testProcessSubcommandWithRootAliasesExecutesAlternativeRootAlias() {
		AtomicInteger executions = new AtomicInteger();
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("help"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("original")
				.literal("help")
				.meta(CommandantKeys.DEFINITION_ID, "help")
				.handler(ctx -> executions.incrementAndGet())
				.build();

		Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter(), List.of("identica", "auth"))
				.register();

		commandManager.commandExecutor().executeCommand(new TestSender(), "auth help").join();
		assertEquals(1, executions.get());
		assertEquals(List.of("identica"), new ArrayList<>(commandManager.rootCommands()));
	}

	@Test
	void testPrefixedAliasesCollapseIntoSharedRootWhenRootAliasesProvided() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("identica help", "auth help"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("original")
				.literal("help")
				.meta(CommandantKeys.DEFINITION_ID, "help")
				.handler(ctx -> {})
				.build();

		List<Command<TestSender>> built = Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter(), List.of("identica", "auth"))
				.build();

		assertEquals(1, built.size());
		assertEquals(List.of("auth"), new ArrayList<>(built.get(0).rootComponent().alternativeAliases()));
	}

	@Test
	void testModifyBeforeRegistration() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("test")
				.meta(CommandantKeys.DEFINITION_ID, "test-def")
				.handler(ctx -> {})
				.build();

		// Use modify to add custom metadata
		Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter())
				.modify(builder -> {
					builder.meta(CustomKey.CUSTOM_VALUE, "modified");
				})
				.register();

		// Verify command was registered with modification
		assertEquals(1, commandManager.commands().size());
	}

	@Test
	void testBuildWithoutRegistering() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("test")
				.meta(CommandantKeys.DEFINITION_ID, "test-def")
				.handler(ctx -> {})
				.build();

		// Build without registering
		List<Command<TestSender>> built = Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter())
				.build();

		// Should return built commands
		assertEquals(1, built.size());

		// Should NOT be registered to manager
		assertEquals(0, commandManager.commands().size());
	}

	@Test
	void testMismatchedUsageTokenFailsFast() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("premium confirm"))
				.usage("{alias} [code]")
				.build();

		Command<TestSender> command = commandManager.commandBuilder("premium")
				.literal("confirm")
				.optional("input", org.incendo.cloud.parser.standard.StringParser.stringParser())
				.meta(CommandantKeys.DEFINITION_ID, "premium-confirm")
				.handler(ctx -> {})
				.build();

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
				Commandant.process(command, commandManager)
						.withDefinition(definition, new TestAdapter())
						.build()
		);

		assertTrue(error.getMessage().contains("code"));
		assertTrue(error.getMessage().contains("input"));
	}

	@Test
	void testDefinitionDisabled() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(false)
				.aliases(List.of("disabled"))
				.build();

		Command<TestSender> command = commandManager.commandBuilder("test")
				.meta(CommandantKeys.DEFINITION_ID, "disabled-def")
				.handler(ctx -> {})
				.build();

		// Process disabled definition
		List<Command.Builder<TestSender>> builders = Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter())
				.builders();

		// Disabled commands should produce no builders
		assertEquals(0, builders.size());
	}

	@Test
	void testCommandWithoutDefinitionId() {
		TestDefinition definition = TestDefinition.builder()
				.enabled(true)
				.aliases(List.of("test"))
				.build();

		// Command without DEFINITION_ID metadata
		Command<TestSender> command = commandManager.commandBuilder("test")
				.handler(ctx -> {})
				.build();

		// Should register as-is even with definition provided
		Commandant.process(command, commandManager)
				.withDefinition(definition, new TestAdapter())
				.register();

		assertEquals(1, commandManager.commands().size());
	}

	// Test helpers

	static class TestSender {
	}

	static class CustomKey {
		static final org.incendo.cloud.key.CloudKey<String> CUSTOM_VALUE =
				org.incendo.cloud.key.CloudKey.of("test:custom", String.class);
	}

	static class TestDefinition {
		private boolean enabled;
		private List<String> aliases;
		private String description;
		private String permission;
		private String usage;

		public boolean isEnabled() {
			return enabled;
		}

		public List<String> getAliases() {
			return aliases;
		}

		public String getDescription() {
			return description;
		}

		public String getPermission() {
			return permission;
		}

		public String getUsage() {
			return usage;
		}

		static Builder builder() {
			return new Builder();
		}

		static class Builder {
			private final TestDefinition def = new TestDefinition();

			Builder enabled(boolean enabled) {
				def.enabled = enabled;
				return this;
			}

			Builder aliases(List<String> aliases) {
				def.aliases = aliases;
				return this;
			}

			Builder description(String description) {
				def.description = description;
				return this;
			}

			Builder permission(String permission) {
				def.permission = permission;
				return this;
			}

			Builder usage(String usage) {
				def.usage = usage;
				return this;
			}

			TestDefinition build() {
				return def;
			}
		}
	}

	static class TestAdapter implements DefinitionAdapter<TestDefinition> {
		@Override
		public boolean isEnabled(@NonNull TestDefinition definition) {
			return definition.isEnabled();
		}

		@Override
		public List<String> getAliases(@NonNull TestDefinition definition) {
			return definition.getAliases();
		}

		@Override
		public String getPermission(@NonNull TestDefinition definition) {
			return definition.getPermission();
		}

		@Override
		public String getDescription(@NonNull TestDefinition definition) {
			return definition.getDescription();
		}

		@Override
		public String getUsage(@NonNull TestDefinition definition) {
			return definition.getUsage();
		}

		@Override
		public Cooldown getCooldown(@NotNull TestDefinition definition) {
			return null;
		}

		@Override
		public Map<String, String> getArguments(@NotNull TestDefinition definition) {
			return null;
		}
	}
}
