package me.whereareiam.commandant.common.registration;

import me.whereareiam.commandant.annotation.Definition;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationCommandRegistrarTest {
	private CommandManager<TestCommandSender> commandManager;
	private CommandRegistrar<TestCommandSender> registrar;
	private Map<String, CommandDefinition> definitions;

	@BeforeEach
	void setUp() {
		this.commandManager = new CommandManager<>(
				ExecutionCoordinator.simpleCoordinator(),
				CommandRegistrationHandler.nullCommandRegistrationHandler()
		) {
			@Override
			public boolean hasPermission(final TestCommandSender sender, final String permission) {
				return sender.hasPermission(permission);
			}
		};

		this.definitions = new HashMap<>();
		this.definitions.put("help", CommandDefinition.builder()
				.aliases(List.of("help"))
				.permission("intercept.help")
				.description("Displays help information")
				.usage("{command} help [page]")
				.build());

		CommandDefinitionRegistration<TestCommandSender> registration = new CommandDefinitionRegistration<>(
				commandManager
		);
		registration.setRootCommand("intercept");

		this.registrar = new AnnotationCommandRegistrar<>(
				registration,
				TestCommandSender.class,
				definitions::get
		);
	}

	@Test
	void registersAnnotatedCommandUsingDefinitions() {
		registrar.register(new HelpCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();

		assertEquals("intercept.help", command.commandPermission().permissionString());
		assertEquals("Displays help information", command.commandDescription().description().textDescription());
		List<String> literals = command.components().stream()
				.map(CommandComponent::name)
				.toList();
		assertEquals(List.of("intercept", "help"), literals.subList(0, 2));
		assertEquals(definitions.get("help"), registrar.resolveDefinition(command).orElse(null));
	}

	@Test
	void registersStandaloneCommandWithoutRoot() {
		CommandDefinitionRegistration<TestCommandSender> registration = new CommandDefinitionRegistration<>(
				commandManager
		);
		// No root command set
		CommandRegistrar<TestCommandSender> standaloneRegistrar = new AnnotationCommandRegistrar<>(
				registration,
				TestCommandSender.class,
				definitions::get
		);

		definitions.put("locale", CommandDefinition.builder()
				.aliases(List.of("locale"))
				.permission("intercept.locale")
				.description("Change locale")
				.usage("locale <locale>")  // No {command} placeholder
				.build());

		standaloneRegistrar.register(new LocaleCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals("locale", command.components().get(0).name());
		assertEquals("intercept.locale", command.commandPermission().permissionString());
	}

	@Test
	void skipsCommandsWithoutDefinitionAnnotation() {
		registrar.register(new CommandsWithoutDefinition());

		assertTrue(commandManager.commands().isEmpty());
	}

	@Test
	void skipsCommandsWithNullDefinition() {
		definitions.put("nonexistent", null);
		registrar.register(new CommandsWithNonexistentDefinition());

		assertTrue(commandManager.commands().isEmpty());
	}

	@Test
	void skipsDisabledCommands() {
		definitions.put("disabled", CommandDefinition.builder()
				.enabled(false)
				.aliases(List.of("disabled"))
				.usage("{command} disabled")
				.build());

		registrar.register(new DisabledCommands());

		assertTrue(commandManager.commands().isEmpty());
	}

	@Test
	void appliesCommandMetadata() {
		definitions.put("reload", CommandDefinition.builder()
				.aliases(List.of("reload"))
				.permission("intercept.reload")
				.description("Reload configuration")
				.usage("{command} reload")
				.build());

		registrar.register(new ReloadCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertEquals("intercept.reload", command.commandPermission().permissionString());
		assertEquals("Reload configuration", command.commandDescription().description().textDescription());
	}

	@Test
	void handlesCommandsWithRequiredArguments() {
		definitions.put("set", CommandDefinition.builder()
				.aliases(List.of("set"))
				.permission("intercept.set")
				.description("Set value")
				.usage("{command} set <key> <value>")
				.build());

		registrar.register(new SetCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		List<String> componentNames = command.components().stream()
				.map(CommandComponent::name)
				.toList();
		assertTrue(componentNames.contains("key"));
		assertTrue(componentNames.contains("value"));
	}

	@Test
	void handlesCommandsWithOptionalArguments() {
		definitions.put("optional", CommandDefinition.builder()
				.aliases(List.of("optional"))
				.permission("intercept.optional")
				.description("Optional command")
				.usage("{command} optional [arg]")
				.build());

		registrar.register(new OptionalCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		Optional<CommandComponent<TestCommandSender>> optionalArg = command.components().stream()
				.filter(c -> c.name().equals("arg"))
				.findFirst();
		assertTrue(optionalArg.isPresent());
		assertFalse(optionalArg.get().required());
	}

	@Test
	void handlesCommandsWithDefaultValues() {
		definitions.put("default", CommandDefinition.builder()
				.aliases(List.of("default"))
				.permission("intercept.default")
				.description("Default command")
				.usage("{command} default [value]")
				.build());

		registrar.register(new DefaultCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		Optional<CommandComponent<TestCommandSender>> defaultArg = command.components().stream()
				.filter(c -> c.name().equals("value"))
				.findFirst();
		assertTrue(defaultArg.isPresent());
		assertTrue(defaultArg.get().hasDefaultValue());
	}

	@Test
	void registersMultipleCommands() {
		definitions.put("cmd1", CommandDefinition.builder()
				.aliases(List.of("cmd1"))
				.permission("intercept.cmd1")
				.usage("{command} cmd1")
				.build());
		definitions.put("cmd2", CommandDefinition.builder()
				.aliases(List.of("cmd2"))
				.permission("intercept.cmd2")
				.usage("{command} cmd2")
				.build());

		registrar.register(new MultipleCommands());

		Collection<Command<TestCommandSender>> commands = commandManager.commands();
		assertEquals(2, commands.size());
	}

	@Test
	void handlesEmptyPermissionString() {
		definitions.put("noperm", CommandDefinition.builder()
				.aliases(List.of("noperm"))
				.permission("")
				.description("No permission")
				.usage("{command} noperm")
				.build());

		registrar.register(new NoPermissionCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertTrue(command.commandPermission().permissionString().isEmpty());
	}

	@Test
	void handlesNullDescription() {
		definitions.put("nodesc", CommandDefinition.builder()
				.aliases(List.of("nodesc"))
				.permission("intercept.nodesc")
				.description(null)
				.usage("{command} nodesc")
				.build());

		registrar.register(new NoDescriptionCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertNotNull(command.commandDescription());
	}

	@Test
	void handlesBlankDescription() {
		definitions.put("blankdesc", CommandDefinition.builder()
				.aliases(List.of("blankdesc"))
				.permission("intercept.blankdesc")
				.description("   ")
				.usage("{command} blankdesc")
				.build());

		registrar.register(new BlankDescriptionCommands());

		Command<TestCommandSender> command = commandManager.commands().iterator().next();
		assertNotNull(command.commandDescription());
	}

	private static final class HelpCommands {
		@Definition("help")
		@org.incendo.cloud.annotations.Command("help [page]")
		public void help(TestCommandSender sender, @Argument("page") @Default("1") int page) {
			sender.setLastPage(page);
		}
	}

	private static final class LocaleCommands {
		@Definition("locale")
		@org.incendo.cloud.annotations.Command("locale <locale>")
		public void locale(TestCommandSender sender, @Argument("locale") String locale) {
			// Test implementation
		}
	}

	private static final class CommandsWithoutDefinition {
		@org.incendo.cloud.annotations.Command("test")
		public void test(TestCommandSender sender) {
			// No @Definition annotation
		}
	}

	private static final class CommandsWithNonexistentDefinition {
		@Definition("nonexistent")
		@org.incendo.cloud.annotations.Command("test")
		public void test(TestCommandSender sender) {
			// Definition will be null
		}
	}

	private static final class DisabledCommands {
		@Definition("disabled")
		@org.incendo.cloud.annotations.Command("disabled")
		public void disabled(TestCommandSender sender) {
			// Command is disabled
		}
	}

	private static final class ReloadCommands {
		@Definition("reload")
		@org.incendo.cloud.annotations.Command("reload")
		public void reload(TestCommandSender sender) {
			// Test implementation
		}
	}

	private static final class SetCommands {
		@Definition("set")
		@org.incendo.cloud.annotations.Command("set <key> <value>")
		public void set(TestCommandSender sender, @Argument("key") String key, @Argument("value") String value) {
			// Test implementation
		}
	}

	private static final class OptionalCommands {
		@Definition("optional")
		@org.incendo.cloud.annotations.Command("optional [arg]")
		public void optional(TestCommandSender sender, @Argument("arg") @Default("default") String arg) {
			// Test implementation
		}
	}

	private static final class DefaultCommands {
		@Definition("default")
		@org.incendo.cloud.annotations.Command("default [value]")
		public void defaultValue(TestCommandSender sender, @Argument("value") @Default("42") int value) {
			// Test implementation
		}
	}

	private static final class MultipleCommands {
		@Definition("cmd1")
		@org.incendo.cloud.annotations.Command("cmd1")
		public void cmd1(TestCommandSender sender) {
			// Test implementation
		}

		@Definition("cmd2")
		@org.incendo.cloud.annotations.Command("cmd2")
		public void cmd2(TestCommandSender sender) {
			// Test implementation
		}
	}

	private static final class NoPermissionCommands {
		@Definition("noperm")
		@org.incendo.cloud.annotations.Command("noperm")
		public void noPermission(TestCommandSender sender) {
			// Test implementation
		}
	}

	private static final class NoDescriptionCommands {
		@Definition("nodesc")
		@org.incendo.cloud.annotations.Command("nodesc")
		public void noDescription(TestCommandSender sender) {
			// Test implementation
		}
	}

	private static final class BlankDescriptionCommands {
		@Definition("blankdesc")
		@org.incendo.cloud.annotations.Command("blankdesc")
		public void blankDescription(TestCommandSender sender) {
			// Test implementation
		}
	}

	private static final class TestCommandSender {
		private final Set<String> permissions = new HashSet<>();
		@SuppressWarnings("unused")
		private int lastPage;
		private final UUID uuid = UUID.randomUUID();

		private TestCommandSender(String... permissions) {
			if (permissions != null) {
				this.permissions.addAll(Arrays.asList(permissions));
			}
		}

		@SuppressWarnings("unused")
		TestCommandSender() {
			this(new String[0]);
		}

		boolean hasPermission(String permission) {
			return permission == null || permission.isBlank() || permissions.contains(permission);
		}

		void setLastPage(int page) {
			this.lastPage = page;
		}

		UUID getUuid() {
			return uuid;
		}
	}
}

