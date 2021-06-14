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

	*loadModules { | set, from |
		var dict = this.cache ?? {
			cache.add(this.name -> Dictionary.new)[this.name];
		};
		var path = this.classFolder+/+set;

		dict[set] ?? {
			if(path.exists){
				this.loadScripts(set);
			} {
				path.mkdir;
				if(from.isNil){
					this.makeTemplates(CodexTemplater(path));
					this.loadScripts(set);
				} {
					dict.add(set -> this.loadModules(from)
						.initialize(this.name++"_"++set++"_"));
					fork { (this.classFolder+/+from).copyScriptsTo(path) };
				}
			};
		};

		^dict[set].deepCopy;
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

	removeModules { this.class.cache.removeAt(moduleSet) }

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
		{ ide=="scqt" }{ this.open_scqt(*keys) }
		{ ide=="scnvim" }{
			var shell = "echo $SHELL".unixCmdGetStdOut.split($/).last;
			shell = shell[..(shell.size - 2)];
			this.open_scvim(shell, true, true, keys: keys);
		}
		{ ide=="scvim" }{
			var shell = "echo $SHELL".unixCmdGetStdOut.split($/).last;
			shell = shell[..(shell.size - 2)];
			this.open_scvim(shell, false, true, keys: keys);
		};
	}

	open_scqt { | ... keys |
		var document = \Document.asClass;
		if(document.notNil, {
			keys.do{ | item |
				var file = this.moduleFolder+/+item.asString++".scd";
				if(File.exists(file), {
					document.perform(\open, file);
				});
			};
		});
	}

	open_scvim { | shell("sh"), neovim(false), vertically(false) ... keys |
		var cmd = "vim", paths = "";
		keys.do({ | key |
			var current = this.moduleFolder+/+key.asString++".scd";
			if(File.exists(current)){
				paths = paths++current++" ";
			};
		});
		if(neovim, { cmd = $n++cmd });
		if(vertically, { cmd = cmd++" -o "}, { cmd = cmd++" -O " });
		paths.do{ | path | cmd=cmd++path };
		if(cmd.runInGnome(shell).not){
			cmd.runInTerminal(shell);
		};
	}

	openModules { this.open(keys: modules.keys.asArray.sort) }

	closeModules {
		if(Platform.ideName=="scqt", {
			var document = \Document.asClass;
			if(document.notNil, {
				document.perform(\allDocuments).do { | doc, index |
					if(doc.dir==this.moduleFolder, {
						doc.close;
					});
				}
			});
		})
	}

	*cache { ^cache.at(this.name) }
	*clearCache { cache.removeAt(this.name).clear }

	doesNotUnderstand { | selector ... args |
		if(know, {
			var module = modules[selector];
			module !? {
				^module.functionPerformList(
					\value,
					modules,
					args
				);
			};
			if(selector.isSetter, {
				if(args[0].isKindOf(modules[selector.asGetter].class), {
					^modules[selector.asGetter] = args[0];
				}, {
					warn(
						"Can only overwrite pseudo-variable module"
						++" with object of the same type."
					);
					^this;
				});
			});
		});
		^this.superPerformList(\doesNotUnderstand, selector, args);
	}
}

CodexModules : Environment {
	var semaphore;

	*new { | folder |
		^super.new.know_(true).initModules(folder);
	}

	initModules { | folder |
		semaphore = Semaphore.new(1);
		this.compileFolder(folder);
	}

	compileFolder { | folder |
		folder !? {
			PathName(folder).files.do { | file |
				var key = file.fileName[0].toLower++file.fileNameWithoutExtension[1..];
				this.add(key.asSymbol -> file.fullPath.compileFile);
			};
		}
	}

	add { | anAssociation |
		this.put(
			anAssociation.key,
			this.class.object.new(
				anAssociation.key,
				anAssociation.value,
				this
			);
		);
	}

	*object { ^CodexObject }

	initialize { | label |
		//Selecting SynthDefs will evaluate/replace modules.
		var synthDefs = this.synthDefs;
		if(synthDefs.isEmpty.not){
			synthDefs.do { | synthDef |
				//Evaluate to get the SynthDef object.
				synthDef = synthDef.value;
				synthDef.metadata.name ?? {
					synthDef.metadata.name = synthDef.name;
				};
				synthDef.name = (label++synthDef.metadata.name).asSymbol;
			};
			fork{
				semaphore.wait;
				synthDefs.do { | synthDef | synthDef.value.add };
				semaphore.signal;
			}
		}
	}

	clear {
		var synthDefs = this.synthDefs;
		if(synthDefs.isEmpty.not){
			fork{
				semaphore.wait;
				synthDefs.do { | synthDef |
					SynthDef.removeAt(synthDef.value.name);
				};
				semaphore.signal;
			}
		};
		super.clear;
	}

	synthDefs {
		^this.array.select { | object |
			object.value.isKindOf(SynthDef);
		}
	}
}

CodexObject {
	var <>key, <>function, <>envir;

	*new { | key, function, envir |
		^super.newCopyArgs(key, function, envir);
	}

	value { | ... args |
		^envir.use({
			if(envir[key].isNil or: { envir[key]==this }){
				envir[key] = function.value(*args);
			};
			envir[key];
		});
	}

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
