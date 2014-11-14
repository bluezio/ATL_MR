package fr.inria.atlanmod.atl_mr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl;
import org.eclipse.m2m.atl.emftvm.ExecEnv;
import org.eclipse.m2m.atl.emftvm.ExecPhase;
import org.eclipse.m2m.atl.emftvm.Model;
import org.eclipse.m2m.atl.emftvm.Rule;
import org.eclipse.m2m.atl.emftvm.trace.TargetElement;
import org.eclipse.m2m.atl.emftvm.trace.TraceLink;
import org.eclipse.m2m.atl.emftvm.trace.TraceLinkSet;
import org.eclipse.m2m.atl.emftvm.trace.TracedRule;

public class ATLMRReducer extends Reducer<Text, BytesWritable, Text, Text> {

	private ATLMapReduceTask reduceTask = new ATLMapReduceTask();
	
    @Override
	protected void reduce(Text key, Iterable<BytesWritable> values, Context context) throws IOException, InterruptedException {
		
		// TODO Parallelize this
		ExecEnv executionEnv = reduceTask.getExecutionEnv();
		ResourceSet rs = reduceTask.getRs();
		Model outModel = reduceTask.getOutModel();
		
		
		for (Entry<String, Object> entry : reduceTask.getRs().getPackageRegistry().entrySet()) {
			rs.getPackageRegistry().put(entry.getKey(), entry.getValue());
		}
		
		ByteArrayInputStream bais = null;
		Iterator<BytesWritable> links = values.iterator();
		
		
		for (BytesWritable b; links.hasNext();) {
			
			b = links.next();
			bais = new ByteArrayInputStream(b.getBytes());
			Resource resource = new BinaryResourceImpl();
			rs.getResources().add(resource);
			resource.setURI(URI.createURI(""));
			resource.load(bais, Collections.emptyMap());

			mergeTraces((TracedRule) resource.getContents().get(0));
		}
		

		outModel.getResource().save(System.out, Collections.emptyMap());
		executionEnv.postApplyAll(reduceTask.getRs());
		outModel.getResource().save(System.out, Collections.emptyMap());
		
	}

	@Override
	protected void setup(Context context) {
		reduceTask.setup(context.getConfiguration(), false);
		reduceTask.getExecutionEnv().setExecutionPhase(ExecPhase.POST);
	}

	@Override
	protected void cleanup(Reducer<Text, BytesWritable, Text, Text>.Context context) throws IOException, InterruptedException {
		Resource outResource = reduceTask.getOutModel().getResource();
		outResource.save(Collections.EMPTY_MAP);
		super.cleanup(context);
		// TODO add resource clean up delete the intermediate models 
	}
	
	private void mergeTraces(TracedRule tracedRule) throws IOException {
		ExecEnv executionEnv = reduceTask.getExecutionEnv();
		Resource outRsc = reduceTask.getOutModel().getResource();
		
		TraceLinkSet traces = executionEnv.getTraces();
		TraceLink traceLink = tracedRule.getLinks().get(0);
		EObject sourceObject = traceLink.getSourceElements().get(0).getObject();
		traceLink.getSourceElements().get(0).setRuntimeObject(sourceObject);
		Rule rule = executionEnv.getRulesMap().get(tracedRule.getRule());
		
		boolean notApplied = true;
		for (Iterator<TracedRule> iter = traces.getRules().iterator(); iter.hasNext() && notApplied;) {
			TracedRule tRule = iter.next();
			if (tRule.getRule().equals(tracedRule.getRule())) {
				tRule.getLinks().add(tracedRule.getLinks().get(0));
				notApplied = false;
			}
		}
		
		if (notApplied) {
			traces.getRules().add(tracedRule);
		}
		
		try {
			for(TargetElement te : traceLink.getTargetElements()) {
					EObject targetObject = te.getObject();
					outRsc.getContents().add(targetObject);
					te.setRuntimeObject(targetObject);
			}
			} catch (Exception e) {
				e.printStackTrace();
			}
		
		rule.createDefaultMappingForTrace(traceLink);
	}

}