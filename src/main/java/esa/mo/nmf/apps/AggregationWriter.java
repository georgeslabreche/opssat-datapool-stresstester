package esa.mo.nmf.apps;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ccsds.moims.mo.mc.aggregation.structures.AggregationParameterValueList;
import org.ccsds.moims.mo.mc.parameter.structures.ParameterValue;

import esa.mo.helpertools.helpers.HelperAttributes;
import esa.mo.mc.impl.provider.AggregationInstance;
import esa.mo.nmf.commonmoadapter.CompleteAggregationReceivedListener;

public class AggregationWriter implements CompleteAggregationReceivedListener {
    
    private static final Logger LOGGER = Logger.getLogger(AggregationWriter.class.getName());
    
    // csv file writer map, one per thread
    private Map<String, PrintWriter> csvWriterMap;
    
    // map to count the numbre of data fetching iterations
    private Map<String, Integer> iterationTrackerMap;

    public AggregationWriter() throws Exception{
        // instanciate our maps
        this.csvWriterMap = new HashMap<String, PrintWriter>();
        this.iterationTrackerMap = new HashMap<String, Integer>();
        
        // get the thread count
        int threadCount = PropertiesManager.getInstance().getThreadCount();
        
        // instanciate a csv file writer for each aggregation
        for(int threadId=1; threadId <= threadCount; threadId++) { 
            // build the aggregation id string
            String aggId = Utils.generateAggregationId(threadId);
            
            // initialize the iteration tracker map for eatch aggregation thread
            this.iterationTrackerMap.put(aggId, 0);
            
            // instanciate a writer for the aggregation
            String csvOuptutFilepath = PropertiesManager.getInstance().getThreadCsvOutputFilepath(threadId);
            this.csvWriterMap.put(aggId, new PrintWriter(new File(csvOuptutFilepath)));
        }
    }
    
    @Override
    public void onDataReceived(AggregationInstance aggregationInstance) {
        if (aggregationInstance == null) {
            LOGGER.log(Level.WARNING, "Received null aggregation instance");
            return;
        }
        
        // get aggregation id
        String aggId = aggregationInstance.getName().getValue();
        
        // get thread id
        int threadId = Utils.getThreadIdFromAggId(aggId);
        
        // If it's the first aggregation data is received then write the csv header row
        if(this.iterationTrackerMap.get(aggId) == 0) {
            writeHeader(aggId); 
        }
        
        // get aggregation timestamp and parameter values
        Long timestamp = aggregationInstance.getTimestamp().getValue();
        List<String> paramValues = getParameterValues(aggregationInstance);
        
        // write param values to csv file
        String csvRow = timestamp + "," + String.join(",", paramValues) + "\n";
        this.csvWriterMap.get(aggId).print(csvRow);
        
        // Get iteration counter for this aggregation
        int iterCounter = incrementIterationTracker(aggId);
        
        // close the writer when we are finished fetching param values for the aggregation
        if(iterCounter >= PropertiesManager.getInstance().getThreadIterations(threadId)) {
            this.csvWriterMap.get(aggId).close();
        }
        
        // TODO: Remove
        System.out.println("PROCESSED AGGREGATION INSTANCE FOR: " + aggId + ", WROTE CSV ROW #" + iterCounter);
        
        // TODO: flush after n iterations
        // TODO: Stop writing after final iteration (destroy/unregister aggregation?)
        // TODO: Stop app after all iterations complete
        // TODO: file writer exception handling

    }
    
    public void writeHeader(String aggId) {
        int threadId = Utils.getThreadIdFromAggId(aggId);
        
        // write the header row
        List<String> paramNames = ApplicationManager.getInstance().getParamNames(threadId);
        String headers = "timestamp," + String.join(",", paramNames) + "\n";
        
        // Write header row to file
        this.csvWriterMap.get(aggId).print(headers);
    }
    
    public int incrementIterationTracker(String aggId) {
        // increment aggregation iteration counter
        int iterCounter = this.iterationTrackerMap.get(aggId);
        iterCounter++;
        
        // update iteration tracker map
        this.iterationTrackerMap.put(aggId, iterCounter);
        
        // return decrement iteration counter value
        return iterCounter;
    }
    
    public List<String> getParameterValues(AggregationInstance aggregationInstance) {

        // the list that will contain all the param values
        List<String> paramValues = new ArrayList<String>();

        // the aggragration param value list
        AggregationParameterValueList aggParamValueList =
                aggregationInstance.getAggregationValue().getParameterSetValues().get(0).getValues();

        // populate the list that will be returned
        for (int i = 0; i < aggParamValueList.size(); i++) {
            ParameterValue paramValue = aggParamValueList.get(i).getValue();
            String paramValueStr = HelperAttributes.attribute2string(paramValue.getRawValue());
            paramValues.add(paramValueStr);
        }

        // return the list
        return paramValues;
    }
    
}
