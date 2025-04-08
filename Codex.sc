Codex {
	classvar <directory, <quark, cache;
	var <moduleSet, <modules, <>know = true;

	*initClass {
		Class.initClassTree(Collection);
		Class.initClassTree(Main);
		Class.initClassTree(Quarks);
		quark = Main.packages.asDict.at(\Codex);
		try {
			directory = File.readAllString(quark+/+"directory.txt");
		}{
			directory = quark+/+"scmodules";
			File.use(
				quark+/+"directory.txt",
				"w",
				{ | file | file.write(directory) };
			);
		};
		cache = Dictionary.new;
		this.allSubclasses.do(_.copyVersions);
	}

	*new { | moduleSet, from |
		^super.newCopyArgs(
			moduleSet ?? { Error("No module set specified").throw }
		).getModules(from).initCodex;
	}

	initCodex { }

	getModules { | from |
		modules = this.class.loadModules(moduleSet, from);
	}

	*preload { | modules | }

	*loadModules { | set, from |
		var classCache, path;
		var modules;

		classCache = this.cache ?? {
			var dict = Dictionary.new;
			cache.add(this.name -> dict);
			dict;
		};

		path = this.classFolder+/+set;

		classCache[set] ?? {
			if (path.exists) {
				this.loadScripts(set);
			} /* else */ {
				path.mkdir;
				if (from.isNil) {
					this.makeTemplates(CodexTemplater(path));
					this.loadScripts(set);
				} /* else */ {
					classCache.add(set -> this.loadModules(from)
						.label_(this.name++"_"++set++"_"));

					(this.classFolder+/+from).copyScriptsTo(path);
				};
			};
		};

		modules = classCache[set].deepCopy;
		this.preload(modules);
		^modules;
	}

	*classFolder { ^(this.directory+/+this.name) }

	*makeTemplates { | templater | }

	*loadScripts { | set |
		this.cache.add(set -> CodexModules(
			folder: this.classFolder+/+set,
			label: this.name++"_"++set++"_"
		));
	}

	*copyVersions {
		var versions = Dictionary.new;
		this.contribute(versions);
		versions = versions.asPairs;
		forBy(1, versions.size - 1, 2, { | index |
			var folder = this.classFolder+/+versions[index - 1];
			if(folder.exists.not){
				versions[index].copyScriptsTo(folder.mkdir);
			};
		});
	}

	*contribute { | versions | }

	moduleFolder { ^(this.class.classFolder+/+moduleSet) }

	moduleSet_{ | newSet, from |
		moduleSet = newSet;
		this.getModules(from);
		this.initCodex;
	}

	reloadModules { this.moduleSet = moduleSet }

	reloadScripts {
		this.removeModules;
		this.reloadModules;
	}

	removeModules {
		this.class.cache.removeAt(moduleSet)
		.removeSynthDefs;
	}

	*moduleSets {
		^PathName(this.classFolder).folders
		.collectAs({ | m | m.folderName.asSymbol }, Set);
	}

	*directory_{ | newPath("~/".standardizePath) |
		directory = newPath;
		File.use(
			quark+/+"directory.txt",
			"w",
			{ | file | file.write(directory) };
		);
	}

	open { | ... keys |
		var ide = Platform.ideName;
		case
		{ ide=="scqt" }{ this.openSCQt(*keys) }
		{ ide=="scnvim" }{
			var shell = "echo $SHELL".unixCmdGetStdOut.split($/).last;
			shell = shell[..(shell.size - 2)];
			this.openSCVim(shell, true, true, keys: keys);
		}
		{ ide=="scvim" }{
			var shell = "echo $SHELL".unixCmdGetStdOut.split($/).last;
			shell = shell[..(shell.size - 2)];
			this.openSCVim(shell, false, true, keys: keys);
		};
	}

	openSCQt { | ... keys |
		var document = \Document.asClass;
		if(document.notNil) {

			keys.do{ | item |
				var file = this.moduleFolder+/+item.asString++".scd";
				if (File.exists(file)) {
					document.perform(\open, file);
				};
			};

		};
	}

	openSCVim { | shell("sh"), neovim(false), vertically(false) ... keys |
		var cmd = "vim", paths = "";

		keys.do { | key |
			var current = this.moduleFolder+/+key.asString++".scd";
			if (File.exists(current)) {
				paths = paths++current++" ";
			};
		};

		if (neovim) {
			cmd = $n++cmd;
		};

		if (vertically) {
			cmd = cmd ++ " -o ";
		} /* else */ {
			cmd = cmd ++ " -O ";
		};

		paths.do { | path |
			cmd = cmd ++ path;
		};

		if (cmd.runInGnome(shell).not) {
			cmd.runInTerminal(shell);
		};
	}

	openScripts { this.open(keys: modules.keys.asArray.sort) }

	closeScripts {
		var current, document = \Document.asClass;
		if (document.notNil and: { Platform.ideName=="scqt"}) {
			current = document.current;

			document.perform(\openDocuments).do { | doc, index |
				if(doc.dir==this.moduleFolder) {
					doc.close;
				};
			};

			current.front;
		};
	}

	*cache { ^cache.at(this.name) }
	*clearCache { cache.removeAt(this.name).clear }

	doesNotUnderstand { | selector ... args |
		if(know) {
			var module = modules[selector];

			module !? {
				^module.functionPerformList(
					\value,
					modules,
					args
				);
			};

			if(selector.isSetter) {
				if(args[0].isKindOf(modules[selector.asGetter].class)) {
					^modules[selector.asGetter] = args[0];
				} /* else */{
					warn(
						"You can only overwrite pseudo-variable module"
						++" with an object of the same type."
					);
					^this;
				};
			};
		};

		^this.superPerformList(\doesNotUnderstand, selector, args);
	}
}

CodexModules : EnvironmentRedirect {
	var <label;

	*new { | folder, label |
		^super.new
		.compileFolder(folder)
		.label_(label)
		.know_(true);
	}

	compileFolder { | folder |
		folder !? {
			folder.getScripts.do { | file |
				var path, key, result;

				path = PathName(file);
				key = path.fileName[0].toLower
				++ path.fileNameWithoutExtension[1..];

				result = file.compileFile;

				result ?? {
					"!Problem in module %%.scd"
					.format(path.pathOnly, key).postln;
				};

				this.add(key.asSymbol -> CodexObject(key, result, this));
			};
		};
	}

	put { | key, obj |
		if (obj.isKindOf(SynthDef)) {
			this.tagSynthDef(obj, label);
		};

		super.put(key.asSymbol, obj);
	}

	tagSynthDef { | synthDef, as("") |
		synthDef.metadata.name ?? {
			synthDef.metadata.name = synthDef.name;
		};

		synthDef.name = (as++synthDef.metadata.name).asSymbol;
		fork { synthDef.add };
	}

	removeSynthDefs {
		fork {
			this.getSynthDefs.do { | synthDef |
				SynthDef.removeAt(synthDef.value.name);
			};
		};
	}

	getSynthDefs {
		// synthDefArr !? { ^synthDefArr };
		^this.envir.array.select { | object |
			case
			{ object.isKindOf(SynthDef) } { true }
			{ object.isKindOf(CodexObject) } {
				object.value.isKindOf(SynthDef);
			}
			{ false }
		};
		// ^synthDefArr;
	}

	initialize {
		this.use {
			this.envir.array.do { | object |
				if (object.isKindOf(CodexObject)) {
					object.value;
				}
			}
		}
	}

	label_{ | newLabel |
		newLabel ?? { ^this };
		label = newLabel;
		this.initialize;
	}

	clear {
		this.removeSynthDefs;
		super.clear;
	}

	asEvent { ^this.envir.asEvent }
}

CodexObject {
	var <>key, <>source, <>envir;
	var evalFunc;

	*new { | key, source, envir |
		^super.newCopyArgs(key.asSymbol, source, envir ? currentEnvironment).initEval;
	}

	initEval {
		evalFunc = { | ... args |
			var inEnvir = envir[key];
			var result;
			if (inEnvir==this || inEnvir.isNil) {

				result = envir.use { source.valueEnvir(*args) } ?? {
					envir = currentEnvironment;
					evalFunc = { | ... args |
						envir.use { source.valueEnvir(*args) };
					};
					this;
				};

				envir.put(key, result);
			};
			result;
		};
	}

	check { | ... args | ^this.value(*args) }

	value { | ... args | ^evalFunc.value(*args) }

	doesNotUnderstand { | selector ... args |
		^try { this.value(selector, *args).perform(selector, *args) }
		{ this.superPerformList(\doesNotUnderstand, selector, *args) }
	}
}

CodexTemplater {
	classvar defaultPath;
	var <>folder;

	*initClass {
		Class.initClassTree(Collection);
		Class.initClassTree(Main);
		Class.initClassTree(Quarks);
		defaultPath = Main.packages.asDict
		.at(\Codex)+/+"Templates";
	}

	*new { | folder |
		folder ?? { Error("No folder set.").throw };
		^super.newCopyArgs(folder.asString);
	}

	synthDef { | templateName("synthDef") |
		this.makeTemplate(templateName, defaultPath+/+"synthDef.scd");
	}

	pattern { | templateName("pattern") |
		this.makeTemplate(templateName, defaultPath+/+"pattern.scd");
	}

	function { | templateName("function") |
		this.makeTemplate(templateName, defaultPath+/+"function.scd");
	}

	synth { | templateName("synth") |
		this.makeTemplate(templateName, defaultPath+/+"node.scd");
	}

	event { | templateName("event") |
		this.makeTemplate(templateName, defaultPath+/+"event.scd");
	}

	array { | templateName("array") |
		this.makeTemplate(templateName, defaultPath+/+"array.scd");
	}

	list { | templateName("list") |
		this.makeTemplate(templateName, defaultPath+/+"list.scd");
	}

	buffer { | templateName("buffer") |
		this.makeTemplate(templateName, defaultPath+/+"buffer.scd");
	}

	blank { | templateName("module") |
		this.makeTemplate(templateName, defaultPath+/+"blank.scd");
	}

	makeTemplate { | templateName, source |
		var fileName, fullPath, i = 0;
		fileName = folder+/+templateName;
		fullPath = fileName++".scd";
		while({ fullPath.exists }){
			i = i + 1;
			fullPath = fileName++i++".scd";
		};
		File.copy(source, fullPath);
	}
}
