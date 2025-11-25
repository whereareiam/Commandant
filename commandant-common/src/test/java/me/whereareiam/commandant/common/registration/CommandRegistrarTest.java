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

import static org.junit.jupiter.api.Assertions.assertEquals;

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

	private static final class HelpCommands {
		@Definition("help")
		@org.incendo.cloud.annotations.Command("help [page]")
		public void help(TestCommandSender sender, @Argument("page") @Default("1") int page) {
			sender.setLastPage(page);
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

