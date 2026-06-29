package ch.njol.skript.conditions;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletionException;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.ExprScriptErrors;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.util.FileUtils;
import ch.njol.util.Kleenean;
import org.skriptlang.skript.lang.script.Script;

@Name("Script Load Errors")
@Description("""
	Loads or reloads a script and checks whether it had any errors.
	The errors can be accessed with the 'last script errors' expression in the else branch.
	Works with local script files and URLs (if URL scripts are enabled in the config).""")
@Example("""
	if reload script "https://example.com/test.sk" does not have errors:
		send "Successfully reloaded."
	else:
		send "&cErrors during reloading:"
		loop errors:
			send "- %loop-value%"
	""")
@Example("""
	if load script "my-script.sk" does not have errors:
		send "Script loaded successfully."
	else:
		set {_errors::*} to errors
		loop {_errors::*}:
			send "&c%loop-value%"
	""")
@Since("2.10")
public class CondScriptLoad extends Condition {

	static {
		Skript.registerCondition(CondScriptLoad.class,
			"(1:(enable|load)|2:reload|3:disable|4:unload) script [file|named] %string% does[n't| not] have error[s]",
			"(1:(enable|load)|2:reload|3:disable|4:unload) script [file|named] %string% has error[s]");
	}

	private int mark;
	private boolean hasErrors;
	private Expression<String> scriptNameExpression;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		this.mark = parseResult.mark;
		this.hasErrors = matchedPattern == 1;
		this.scriptNameExpression = (Expression<String>) exprs[0];
		return true;
	}

	@Override
	public boolean check(Event event) {
		String name = scriptNameExpression.getSingle(event);
		if (name == null)
			return !hasErrors;

		RetainingLogHandler logHandler = new RetainingLogHandler().start();
		try {
			boolean isUrl = name.toLowerCase(Locale.ENGLISH).startsWith("http://")
				|| name.toLowerCase(Locale.ENGLISH).startsWith("https://");
			if (isUrl) {
				if (!SkriptConfig.allowUrlScripts.value()) {
					Skript.error("URL scripts are disabled in the config.");
				} else {
					Script script = ScriptLoader.getScriptByName(name);
					switch (mark) {
						case 1:
						case 2:
							if (script != null)
								ScriptLoader.unloadScript(script);
							try {
								ScriptLoader.loadScriptFromUrl(new URL(name), logHandler, true).join();
							} catch (MalformedURLException e) {
								Skript.error("Invalid URL for script: " + name);
							} catch (CompletionException e) {
								Skript.exception(e.getCause() != null ? e.getCause() : e, "An error occurred while loading script from URL: " + name);
							}
							break;
						case 3:
						case 4:
							if (script != null)
								ScriptLoader.unloadScript(script);
							break;
					}
				}
			} else {
				File scriptFile = ScriptLoader.getScriptFromName(name);
				if (scriptFile != null && scriptFile.exists()) {
					FileFilter filter = ScriptLoader.getDisabledScriptsFilter();
					switch (mark) {
						case 1:
							if (!ScriptLoader.getLoadedScripts().contains(ScriptLoader.getScript(scriptFile))) {
								if (filter.accept(scriptFile)) {
									try {
										scriptFile = FileUtils.move(scriptFile, new File(scriptFile.getParentFile(), scriptFile.getName().substring(ScriptLoader.DISABLED_SCRIPT_PREFIX_LENGTH)), false);
									} catch (IOException ex) {
										Skript.exception(ex, "Error while enabling script file: " + name);
									}
								}
								loadScriptsSync(scriptFile, logHandler);
							}
							break;
						case 2:
							if (!filter.accept(scriptFile)) {
								Script script = ScriptLoader.getScript(scriptFile);
								if (script != null)
									ScriptLoader.unloadScript(script);
								loadScriptsSync(scriptFile, logHandler);
							}
							break;
						case 3:
							if (!filter.accept(scriptFile)) {
								Script script = ScriptLoader.getScript(scriptFile);
								if (script != null)
									ScriptLoader.unloadScript(script);
								try {
									FileUtils.move(scriptFile, new File(scriptFile.getParentFile(), ScriptLoader.DISABLED_SCRIPT_PREFIX + scriptFile.getName()), false);
								} catch (IOException ex) {
									Skript.exception(ex, "Error while disabling script file: " + name);
								}
							}
							break;
						case 4:
							if (!filter.accept(scriptFile)) {
								Script script = ScriptLoader.getScript(scriptFile);
								if (script != null)
									ScriptLoader.unloadScript(script);
							}
							break;
					}
				}
			}
		} finally {
			if (!logHandler.isStopped())
				logHandler.stop();
			ExprScriptErrors.storeFrom(logHandler);
		}

		return logHandler.hasErrors() == this.hasErrors;
	}

	private static void loadScriptsSync(File scriptFile, RetainingLogHandler logHandler) {
		try {
			ScriptLoader.loadScripts(scriptFile, logHandler, true).join();
		} catch (CompletionException e) {
			Skript.exception(e.getCause() != null ? e.getCause() : e, "An error occurred while loading script: " + scriptFile.getName());
		}
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		String start = switch (mark) {
			case 1 -> "enable";
			case 3 -> "disable";
			case 2 -> "reload";
			default -> "unload";
		};
		return start + " script " + scriptNameExpression.toString(event, debug) + (hasErrors ? " has errors" : " doesn't have errors");
	}
}
