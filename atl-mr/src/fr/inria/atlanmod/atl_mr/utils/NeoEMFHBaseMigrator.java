/*******************************************************************************
KyanosHbaseCreator * Copyright (c) 2014 Abel G�mez.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Abel G�mez - initial API and implementation
 ******************************************************************************/
package fr.inria.atlanmod.atl_mr.utils;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import fr.inria.atlanmod.kyanos.core.KyanosResourceFactory;
import fr.inria.atlanmod.kyanos.core.impl.KyanosHbaseResourceImpl;
import fr.inria.atlanmod.kyanos.util.KyanosURI;

public class NeoEMFHBaseMigrator {

	private static final Logger LOG = Logger.getLogger(NeoEMFHBaseMigrator.class.getName());

	private static final String IN = "mip";

	private static final String OUT = "mop";

	private static final String E_PACKAGE = "mpck";

	public static void main(String[] args) {
		Options options = new Options();

		Option inputOpt = OptionBuilder.create(IN);
		inputOpt.setArgName("INPUT");
		inputOpt.setDescription("Input file, both of xmi and zxmi extensions are supported");
		inputOpt.setArgs(1);
		inputOpt.setRequired(true);


		Option outputOpt = OptionBuilder.create(OUT);
		outputOpt.setArgName("OUTPUT");
		outputOpt.setDescription("Output HBase resource URI");
		outputOpt.setArgs(1);
		outputOpt.setRequired(true);

		Option inClassOpt = OptionBuilder.create(E_PACKAGE);
		inClassOpt.setArgName("METAMODEL");
		inClassOpt.setDescription("URI of the ecore Metamodel");
		inClassOpt.setArgs(1);
		inClassOpt.setRequired(true);

		options.addOption(inputOpt);
		options.addOption(outputOpt);
		options.addOption(inClassOpt);

		CommandLineParser parser = new PosixParser();

		try {
			CommandLine commandLine = parser.parse(options, args);

			URI sourceUri = URI.createFileURI(commandLine.getOptionValue(IN));
			URI targetUri = URI.createURI(commandLine.getOptionValue(OUT));
			URI metamodelUri = URI.createFileURI(commandLine.getOptionValue(E_PACKAGE));

			NeoEMFHBaseMigrator.class.getClassLoader().loadClass(commandLine.getOptionValue(E_PACKAGE)).getMethod("init").invoke(null);
			//org.eclipse.gmt.modisco.java.kyanos.impl.JavaPackageImpl.init();


			ResourceSet resourceSet = new ResourceSetImpl();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("zxmi", new XMIResourceFactoryImpl());
			resourceSet.getResourceFactoryRegistry().getProtocolToFactoryMap().put(KyanosURI.KYANOS_HBASE_SCHEME, KyanosResourceFactory.eINSTANCE);

			//Registering the metamodel
			//			Resource MMResource = resourceSet.createResource(metamodelUri);
			//			MMResource.load(Collections.EMPTY_MAP);
			//			ATLMRUtils.registerPackages(resourceSet, MMResource);
			//Loading the XMI resource
			Resource sourceResource = resourceSet.createResource(sourceUri);
			Map<String, Object> loadOpts = new HashMap<String, Object>();

			if ("zxmi".equals(sourceUri.fileExtension())) {
				loadOpts.put(XMIResource.OPTION_ZIP, Boolean.TRUE);
			}

			Runtime.getRuntime().gc();
			long initialUsedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			LOG.log(Level.INFO, MessageFormat.format("Used memory before loading: {0}",
					ATLMRUtils.byteCountToDisplaySize(initialUsedMemory)));
			LOG.log(Level.INFO, "Loading source resource");
			sourceResource.load(loadOpts);
			LOG.log(Level.INFO, "Source resource loaded");
			Runtime.getRuntime().gc();
			long finalUsedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			LOG.log(Level.INFO, MessageFormat.format("Used memory after loading: {0}",
					ATLMRUtils.byteCountToDisplaySize(finalUsedMemory)));
			LOG.log(Level.INFO, MessageFormat.format("Memory use increase: {0}",
					ATLMRUtils.byteCountToDisplaySize(finalUsedMemory - initialUsedMemory)));


			Resource targetResource = resourceSet.createResource(targetUri);

			Map<String, Object> saveOpts = new HashMap<String, Object>();
			targetResource.save(saveOpts);

			LOG.log(Level.INFO, "Start moving elements");
			targetResource.getContents().clear();
			targetResource.getContents().addAll(sourceResource.getContents());
			LOG.log(Level.INFO, "End moving elements");
			LOG.log(Level.INFO, "Start saving");
			targetResource.save(saveOpts);
			LOG.log(Level.INFO, "Saved");

			if (targetResource instanceof KyanosHbaseResourceImpl) {
				KyanosHbaseResourceImpl.shutdownWithoutUnload((KyanosHbaseResourceImpl) targetResource);
			} else {
				targetResource.unload();
			}

		} catch (ParseException e) {
			ATLMRUtils.showError(e.toString());
			ATLMRUtils.showError("Current arguments: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar <this-file.jar>", options, true);
		} catch (Throwable e) {
			ATLMRUtils.showError(e.toString());
			e.printStackTrace();
		}
	}
}
