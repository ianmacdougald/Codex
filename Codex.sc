Codex {
	classvar <directory, quark, cache;
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
		this.subclasses.do(_.copyVersions);
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
		var dict, path;
		var modules;

		dict = this.cache ?? {
			var classCache = Dictionary.new;
			cache.add(this.name -> classCache);
			classCache;
		};

		path = this.classFolder+/+set;

		dict[set] ?? {
			if (path.exists) {
				this.loadScripts(set);
			} /* else */ {
				path.mkdir;
				if (from.isNil) {
					this.makeTemplates(CodexTemplater(path));
					this.loadScripts(set);
				} /* else */ {
					dict.add(set -> this.loadModules(from)
						.initialize(this.name++"_"++set++"_"));

					fork { (this.classFolder+/+from).copyScriptsTo(path) };
				};
			};
		};

		modules = dict[set].deepCopy;
		this.preload(modules);
		^modules;
	}

	*classFolder { ^(this.directory+/+this.name) }

	*makeTemplates { | templater | }

	*loadScripts { | set |
		this.cache.add(set -> CodexModules(this.classFolder+/+set)
			.initialize(this.name++"_"++set++"_"));
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

	openModules { this.open(keys: modules.keys.asArray.sort) }

	closeModules {
		if(Platform.ideName=="scqt") {
			var document = \Document.asClass;
			if(document.notNil) {
				document.perform(\allDocuments).do { | doc, index |
					if(doc.dir==this.moduleFolder) {
						doc.close;
					};
				};
			};
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
						"Can only overwrite pseudo-variable module"
						++" with object of the same type."
					);
					^this;
				};
			};
		};

		^this.superPerformList(\doesNotUnderstand, selector, args);
	}

	notes {
		^try { (this.moduleFolder+/+"notes.txt").load };
	}
}

CodexModules : Environment {
	var semaphore, synthDefArr;

	*new { | folder |
		^super.new.know_(true).initModules(folder);
	}

	initModules { | folder |
		semaphore = Semaphore.new(1);
		this.compileFolder(folder);
	}

	compileFolder { | folder |
		folder !? {
			folder.getScripts.do { | file |
				var path, key;

				path = PathName(file);
				key = path.fileName[0].toLower
				++ path.fileNameWithoutExtension[1..];

				this.add(key.asSymbol -> file.compileFile);
			};
		};
	}

	add { | anAssociation |
		this.put(
			anAssociation.key,
			CodexObject.new(
				anAssociation.key,
				anAssociation.value,
				this
			);
		);
	}

	initialize { | label |
		var synthDefs;

		synthDefArr = this.getSynthDefs;

		this.use {
			this.array.copy.do { | object |
				try { object = object.check };

				if (object.isKindOf(SynthDef)) {
					synthDefArr = synthDefArr.add(object);
					object.metadata.name ?? {
						object.metadata.name = object.name;
					};
					object.name = (label++object.metadata.name).asSymbol;
				};
			};
		};

		this.addSynthDefs;
	}

	addSynthDefs {
		fork {
			semaphore.wait;
			synthDefArr.do { | synthDef | synthDef.add };
			semaphore.signal;
		};
	}

	removeSynthDefs {
		fork {
			semaphore.wait;
			synthDefArr.do { | synthDef |
				SynthDef.removeAt(synthDef.value.name);
			};
			semaphore.signal;
		};
	}

	getSynthDefs {
		^synthDefArr ? [];
	}

	findSynthDefs {
		synthDefArr = this.array.select { | object |
			object.isKindOf(SynthDef);
		};
		^synthDefArr;
	}

	tagSynthDefs { | label |
		this.findSynthDefs.do { | synthDef |
			synthDef.metadata.name ?? {
				synthDef.metadata.name = synthDef.name;
			};
			synthDef.name = (label++synthDef.metadata.name).asSymbol;
		};

		this.addSynthDefs;
	}

	clear {
		if (synthDefArr.notEmpty) {
			this.removeSynthDefs;
		};
		synthDefArr = nil;
		super.clear;
	}
}

CodexObject {
	var <>key, <>function, <>envir;
	var evalFunc;

	*new { | key, function, envir |
		^super.newCopyArgs(key, function, envir).initEval;
	}

	initEval {
		evalFunc = { | ... args |
			if (envir[key]==this || envir[key].isNil) {
				envir[key] = envir.use { function.valueEnvir(*args) } ?? {
					envir = currentEnvironment;
					evalFunc = { | ... args |
						envir.use { function.valueEnvir(*args) };
					};
					this;
				};
			};
			envir[key];
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
