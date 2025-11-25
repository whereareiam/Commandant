package me.whereareiam.commandant.common.registration;

import me.whereareiam.commandant.annotation.Definition;
import me.whereareiam.commandant.model.CommandDefinition;
import me.whereareiam.commandant.registration.CommandRegistrar;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
		this.definitions.put("help", def(true, "{command} help [page]", "intercept.help", "Displays help information", "help"));

		this.registrar = newRegistrar("intercept");
	}

	/* ---------------- Helpers ---------------- */

	private CommandRegistrar<TestCommandSender> newRegistrar(String root) {
		CommandDefinitionRegistration<TestCommandSender> registration = new CommandDefinitionRegistration<>(commandManager);
		if (root != null) registration.setRootCommand(root);

		return new AnnotationCommandRegistrar<>(
				registration,
				TestCommandSender.class,
				definitions::get
		);
	}

	private static CommandDefinition def(
			boolean enabled,
			String usage,
			String permission,
			String description,
			String... aliases
	) {
		return CommandDefinition.builder()
				.enabled(enabled)
				.usage(usage)
				.permission(permission)
				.description(description)
				.aliases(aliases == null ? null : List.of(aliases))
				.build();
	}

	private Command<TestCommandSender> onlyCommand() {
		Collection<Command<TestCommandSender>> cmds = commandManager.commands();
		assertEquals(1, cmds.size(), "Expected exactly 1 command, got: " + cmds.size());
		return cmds.iterator().next();
	}

	private List<String> literalNames(Command<TestCommandSender> command) {
		List<String> names = new ArrayList<>();
		for (CommandComponent<TestCommandSender> c : command.components()) {
			if (c.type() != CommandComponent.ComponentType.LITERAL) continue;
			names.add(c.name());
		}
		return names;
	}

	private CommandComponent<TestCommandSender> nthLiteral(Command<TestCommandSender> command, int index) {
		int i = -1;
		for (CommandComponent<TestCommandSender> c : command.components()) {
			if (c.type() != CommandComponent.ComponentType.LITERAL) continue;
			if (++i == index) return c;
		}
		fail("No literal at index " + index + " in " + literalNames(command));
		return null; // unreachable
	}

	private static boolean hasComponent(Command<TestCommandSender> command, String name) {
		for (CommandComponent<TestCommandSender> c : command.components()) {
			if (Objects.equals(c.name(), name)) return true;
		}
		return false;
	}

	/* ---------------- Tests ---------------- */

	@Test
	void registersAnnotatedCommandUsingDefinitions() {
		registrar.register(new HelpCommands());

		Command<TestCommandSender> command = onlyCommand();
		assertEquals("intercept.help", command.commandPermission().permissionString());
		assertEquals("Displays help information", command.commandDescription().description().textDescription());

		assertEquals(List.of("intercept", "help"), literalNames(command));
		assertEquals(definitions.get("help"), registrar.resolveDefinition(command).orElse(null));
	}

	@Test
	void registersStandaloneCommandWithoutRoot() {
		// root intentionally not set
		CommandRegistrar<TestCommandSender> standalone = newRegistrar(null);

		definitions.put("locale", def(true, "locale <locale>", "intercept.locale", "Change locale", "locale"));
		standalone.register(new LocaleCommands());

		Command<TestCommandSender> command = onlyCommand();
		assertEquals(List.of("locale"), literalNames(command));
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
		definitions.put("disabled", def(false, "{command} disabled", null, null, "disabled"));
		registrar.register(new DisabledCommands());
		assertTrue(commandManager.commands().isEmpty());
	}

	@Test
	void appliesCommandMetadata_whenNotBlank() {
		definitions.put("reload", def(true, "{command} reload", "intercept.reload", "Reload configuration", "reload"));
		registrar.register(new ReloadCommands());

		Command<TestCommandSender> command = onlyCommand();
		assertEquals("intercept.reload", command.commandPermission().permissionString());
		assertEquals("Reload configuration", command.commandDescription().description().textDescription());
	}

	@Test
	void handlesRequiredArguments() {
		definitions.put("set", def(true, "{command} set <key> <value>", "intercept.set", "Set value", "set"));
		registrar.register(new SetCommands());

		Command<TestCommandSender> command = onlyCommand();
		assertTrue(hasComponent(command, "key"));
		assertTrue(hasComponent(command, "value"));
	}

	@Test
	void handlesOptionalArguments() {
		definitions.put("optional", def(true, "{command} optional [arg]", "intercept.optional", "Optional command", "optional"));
		registrar.register(new OptionalCommands());

		Command<TestCommandSender> command = onlyCommand();

		CommandComponent<TestCommandSender> arg = command.components().stream()
				.filter(c -> Objects.equals(c.name(), "arg"))
				.findFirst()
				.orElseThrow();

		assertFalse(arg.required());
	}

	@Test
	void handlesDefaultValues() {
		definitions.put("default", def(true, "{command} default [value]", "intercept.default", "Default command", "default"));
		registrar.register(new DefaultCommands());

		Command<TestCommandSender> command = onlyCommand();

		CommandComponent<TestCommandSender> value = command.components().stream()
				.filter(c -> Objects.equals(c.name(), "value"))
				.findFirst()
				.orElseThrow();

		assertTrue(value.hasDefaultValue());
	}

	@Test
	void registersMultipleCommands() {
		definitions.put("cmd1", def(true, "{command} cmd1", "intercept.cmd1", null, "cmd1"));
		definitions.put("cmd2", def(true, "{command} cmd2", "intercept.cmd2", null, "cmd2"));

		registrar.register(new MultipleCommands());

		assertEquals(2, commandManager.commands().size());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void blankOrNullPermission_isNotApplied(String permission) {
		definitions.put("noperm", CommandDefinition.builder()
				.aliases(List.of("noperm"))
				.permission(permission)
				.description("No permission")
				.usage("{command} noperm")
				.build());

		registrar.register(new NoPermissionCommands());

		Command<TestCommandSender> command = onlyCommand();
		assertTrue(command.commandPermission().permissionString().isEmpty());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void blankOrNullDescription_isNotApplied(String description) {
		definitions.put("nodesc", CommandDefinition.builder()
				.aliases(List.of("nodesc"))
				.permission("intercept.nodesc")
				.description(description)
				.usage("{command} nodesc")
				.build());

		registrar.register(new NoDescriptionCommands());

		Command<TestCommandSender> command = onlyCommand();
		// Description object exists, but should be the default/empty one if you didn't apply metadata:
		assertTrue(command.commandDescription().description().equals(Description.empty())
						|| command.commandDescription().description().textDescription().isEmpty(),
				"Expected empty/default description when not applied");
	}

	@Test
	void usesDefinitionAliasesInsteadOfCommandAnnotation() {
		definitions.put("locale-multi", CommandDefinition.builder()
				.aliases(List.of("locale", "language", "lang"))
				.permission("intercept.locale")
				.description("Change locale")
				.usage("{command} {alias} <locale>")
				.build());

		registrar.register(new MultiAliasCommands());

		Command<TestCommandSender> command = onlyCommand();
		CommandComponent<TestCommandSender> aliasLiteral = nthLiteral(command, 1); // intercept + alias
		Set<String> allAliases = new HashSet<>();
		allAliases.add(aliasLiteral.name());
		allAliases.addAll(aliasLiteral.alternativeAliases());

		assertEquals(Set.of("locale", "language", "lang"), allAliases);
	}

	@Test
	void usesDefinitionUsageForArgumentOrder_evenIfAnnotationDiffers() {
		definitions.put("locale-target", CommandDefinition.builder()
				.aliases(List.of("locale"))
				.permission("intercept.locale.target")
				.description("Change player locale")
				.usage("{command} {alias} <player> <locale>") // definition order: player -> locale
				.build());

		registrar.register(new LocaleTargetCommands());

		Command<TestCommandSender> command = onlyCommand();
		List<String> namesAfterLiterals = command.components().stream()
				.filter(c -> c.type() != CommandComponent.ComponentType.LITERAL)
				.map(CommandComponent::name)
				.toList();

		assertEquals(List.of("player", "locale"), namesAfterLiterals);
	}

	@Test
	void ignoresCommandAnnotationContent_forAliases() {
		definitions.put("lang-test", CommandDefinition.builder()
				.aliases(List.of("lang1", "lang2"))
				.permission("intercept.lang")
				.description("Language test")
				.usage("{command} {alias}")
				.build());

		registrar.register(new IgnoredAnnotationCommands());

		Command<TestCommandSender> command = onlyCommand();
		CommandComponent<TestCommandSender> aliasLiteral = nthLiteral(command, 1);

		Set<String> allAliases = new HashSet<>();
		allAliases.add(aliasLiteral.name());
		allAliases.addAll(aliasLiteral.alternativeAliases());

		assertEquals(Set.of("lang1", "lang2"), allAliases);
		assertFalse(allAliases.contains("lang123"));
	}

	@Test
	void registersMultiWordAliasSubcommand() {
		definitions.put("mw", CommandDefinition.builder()
				.aliases(List.of("user add"))
				.permission("intercept.user.add")
				.description("Add user")
				.usage("{command} {alias} <name>")
				.build());

		registrar.register(new MultiWordAliasCommands());

		Command<TestCommandSender> cmd = onlyCommand();
		assertEquals(List.of("intercept", "user", "add"), literalNames(cmd));
		assertTrue(hasComponent(cmd, "name"));
	}

	@Test
	void registersMultiWordAliasStandalone() {
		CommandRegistrar<TestCommandSender> standalone = newRegistrar(null);

		definitions.put("mw-standalone", CommandDefinition.builder()
				.aliases(List.of("user add"))
				.permission("intercept.user.add")
				.description("Add user")
				.usage("{alias} <name>") // no {command}
				.build());

		standalone.register(new MultiWordAliasStandaloneCommands());

		Command<TestCommandSender> cmd = onlyCommand();
		assertEquals(List.of("user", "add"), literalNames(cmd));
		assertTrue(hasComponent(cmd, "name"));
	}

	@Test
	void emptyAliases_throws() {
		definitions.put("no-alias", CommandDefinition.builder()
				.aliases(Collections.emptyList())
				.usage("{command} test")
				.build());

		assertThrows(IllegalArgumentException.class, () -> registrar.register(new NoAliasCommands()));
	}

	/* ---------------- Containers ---------------- */

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
		public void locale(TestCommandSender sender, @Argument("locale") String locale) {}
	}

	private static final class CommandsWithoutDefinition {
		@org.incendo.cloud.annotations.Command("test")
		public void test(TestCommandSender sender) {}
	}

	private static final class CommandsWithNonexistentDefinition {
		@Definition("nonexistent")
		@org.incendo.cloud.annotations.Command("test")
		public void test(TestCommandSender sender) {}
	}

	private static final class DisabledCommands {
		@Definition("disabled")
		@org.incendo.cloud.annotations.Command("disabled")
		public void disabled(TestCommandSender sender) {}
	}

	private static final class ReloadCommands {
		@Definition("reload")
		@org.incendo.cloud.annotations.Command("reload")
		public void reload(TestCommandSender sender) {}
	}

	private static final class SetCommands {
		@Definition("set")
		@org.incendo.cloud.annotations.Command("set <key> <value>")
		public void set(TestCommandSender sender, @Argument("key") String key, @Argument("value") String value) {}
	}

	private static final class OptionalCommands {
		@Definition("optional")
		@org.incendo.cloud.annotations.Command("optional [arg]")
		public void optional(TestCommandSender sender, @Argument("arg") @Default("default") String arg) {}
	}

	private static final class DefaultCommands {
		@Definition("default")
		@org.incendo.cloud.annotations.Command("default [value]")
		public void defaultValue(TestCommandSender sender, @Argument("value") @Default("42") int value) {}
	}

	private static final class MultipleCommands {
		@Definition("cmd1")
		@org.incendo.cloud.annotations.Command("cmd1")
		public void cmd1(TestCommandSender sender) {}

		@Definition("cmd2")
		@org.incendo.cloud.annotations.Command("cmd2")
		public void cmd2(TestCommandSender sender) {}
	}

	private static final class NoPermissionCommands {
		@Definition("noperm")
		@org.incendo.cloud.annotations.Command("noperm")
		public void noPermission(TestCommandSender sender) {}
	}

	private static final class NoDescriptionCommands {
		@Definition("nodesc")
		@org.incendo.cloud.annotations.Command("nodesc")
		public void noDescription(TestCommandSender sender) {}
	}

	private static final class MultiAliasCommands {
		@Definition("locale-multi")
		@org.incendo.cloud.annotations.Command("locale <locale>") // ignored for aliases
		public void locale(TestCommandSender sender, @Argument("locale") String locale) {}
	}

	private static final class LocaleTargetCommands {
		@Definition("locale-target")
		// intentionally swapped order vs definition:
		@org.incendo.cloud.annotations.Command("locale <locale> <player>")
		public void localeTarget(
				TestCommandSender sender,
				@Argument("player") String player,
				@Argument("locale") String locale
		) {}
	}

	private static final class IgnoredAnnotationCommands {
		@Definition("lang-test")
		@org.incendo.cloud.annotations.Command("lang123") // should be ignored
		public void langTest(TestCommandSender sender) {}
	}

	private static final class MultiWordAliasCommands {
		@Definition("mw")
		@org.incendo.cloud.annotations.Command("whatever <name>") // should be ignored
		public void userAdd(TestCommandSender sender, @Argument("name") String name) {}
	}

	private static final class MultiWordAliasStandaloneCommands {
		@Definition("mw-standalone")
		@org.incendo.cloud.annotations.Command("whatever <name>") // should be ignored
		public void userAdd(TestCommandSender sender, @Argument("name") String name) {}
	}

	private static final class NoAliasCommands {
		@Definition("no-alias")
		@org.incendo.cloud.annotations.Command("test")
		public void test(TestCommandSender sender) {}
	}

	/* ---------------- Sender ---------------- */

	private static final class TestCommandSender {
		private final Set<String> permissions = new HashSet<>();
		@SuppressWarnings("unused")
		private int lastPage;

		private TestCommandSender(String... permissions) {
			if (permissions != null) this.permissions.addAll(Arrays.asList(permissions));
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
	}
}