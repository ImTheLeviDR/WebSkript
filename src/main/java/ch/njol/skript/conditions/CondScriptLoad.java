package ch.njol.skript.conditions;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.log.LogEntry;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.util.FileUtils;
import ch.njol.skript.expressions.ExprScriptErrors;
import ch.njol.util.Kleenean;
import org.skriptlang.skript.lang.script.Script;

@Name("Script Load Errors")
@Description("Loads or reloads a script and checks if it had any errors.")
@Example("""
	if reload script "https://levikk.s3.pl-waw.scw.cloud/test.sk" does not have errors:
		send "Successfully reloaded."
	else:
		send "Errors during reloading."
		loop errors:
			send "%loop-value%"
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
		
		boolean isUrl = name.toLowerCase(java.util.Locale.ENGLISH).startsWith("http://") || name.toLowerCase(java.util.Locale.ENGLISH).startsWith("https://");
		if (isUrl) {
			if (!ch.njol.skript.SkriptConfig.allowUrlScripts.value()) {
				logHandler.stop();
				return hasErrors;
			}
			Script script = ScriptLoader.getScriptByName(name);
			switch (mark) {
				case 1:
				case 2:
					if (script != null)
						ScriptLoader.unloadScript(script);
					try {
						ScriptLoader.loadScriptFromUrl(new java.net.URL(name), logHandler).join();
					} catch (java.net.MalformedURLException e) {
						Skript.exception(e, "Invalid URL for script: " + name);
					}
					break;
				case 3:
				case 4:
					if (script != null)
						ScriptLoader.unloadScript(script);
					break;
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
							ScriptLoader.loadScripts(scriptFile, logHandler).join();
						}
						break;
					case 2:
						if (!filter.accept(scriptFile)) {
							Script script = ScriptLoader.getScript(scriptFile);
							if (script != null)
								ScriptLoader.unloadScript(script);
							ScriptLoader.loadScripts(scriptFile, logHandler).join();
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

		logHandler.stop();
		
		List<String> errors = new ArrayList<>();
		for (LogEntry entry : logHandler.getErrors()) {
			errors.add(entry.getMessage());
		}
		ExprScriptErrors.lastErrors.set(errors);
		
		boolean hadErrors = !errors.isEmpty();
		return hadErrors == this.hasErrors;
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
