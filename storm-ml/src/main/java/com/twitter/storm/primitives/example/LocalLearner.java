package com.twitter.storm.primitives.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;

import org.apache.log4j.Logger;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.transactional.ICommitter;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

import com.twitter.algorithms.Learner;
import com.twitter.data.Example;
import com.twitter.data.HashAll;

public class LocalLearner extends BaseRichBolt implements ICommitter {
    public static Logger LOG = Logger.getLogger(LocalLearner.class);

    private int dimension;
    OutputCollector _collector;
    List<Example> buffer = new ArrayList<Example>();
    Object id;
    OutputCollector collector;
    HashAll hashFunction;
    Learner learner;
    double[] weightVector;
    String memcached_servers;
    MemcachedClient memcache;

    public LocalLearner(int dimension, String memcached_servers) throws IOException {
        this(dimension, new Learner(dimension), memcached_servers);
    }

    public LocalLearner(int dimension, Learner onlinePerceptron, String memcached_servers) {// , HashAll hashAll) {
        try {
            this.dimension = dimension;
            this.learner = onlinePerceptron;
            // this.hashFunction = hashAll;
            this.memcached_servers = memcached_servers;
            weightVector = new double[dimension];
            learner.setWeights(weightVector);
        } catch (Exception e) {

        }
    }

    public void execute(Tuple tuple) {
        Example example = new Example(2);
        example.x[0] = (Double) tuple.getValue(0);
        example.x[1] = (Double) tuple.getValue(1);
        example.label = (Double) tuple.getValue(2);
        learner.update(example, 1, memcache);
        LOG.debug("local weights" + learner.getWeightsArray() + " parallel weights "
                + learner.getParallelUpdateWeight());
        _collector.emit(new Values(learner.getWeightsArray(), learner.getParallelUpdateWeight()));
        _collector.ack(tuple);
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("weight_vector", "parallel_weight"));
    }

    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
        learner.initWeights(weightVector);
        _collector = collector;
        memcache = (MemcachedClient) context.getTaskData();
        weightVector = (double[]) context.getTaskData();
        try {
            memcache = new MemcachedClient(AddrUtil.getAddresses(memcached_servers));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
