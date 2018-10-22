/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim;

import java.util.concurrent.atomic.AtomicBoolean;

import kilim.nio.NioSelectorScheduler.RegistrationTask;
import kilim.timerservice.Timer;

/**
 * This is a basic FIFO Executor. It maintains a list of runnable tasks and hands them out to WorkerThreads. Note
 * that we don't maintain a list of all tasks, but we will at some point when we introduce monitoring/watchdog
 * services. Paused tasks are not GC'd because their PauseReasons ought to be registered with some other live
 * object.
 *
 */
public class Scheduler {
    private static final int defaultQueueSize_ = Integer.MAX_VALUE;
    public static volatile Scheduler defaultScheduler = null;
    public static int defaultNumberThreads;
    private static final ThreadLocal<Task> taskMgr_ = new ThreadLocal<Task>();
    public static Logger defaultLogger = new BasicLogger();

    private AffineThreadPool affinePool_;
    protected AtomicBoolean shutdown = new AtomicBoolean(false);
    
    private Logger logger = defaultLogger;

    static {
        String s = System.getProperty("kilim.Scheduler.numThreads");
        if (s!=null)
            try {
                defaultNumberThreads = Integer.parseInt(s);
            } catch (Exception e) {
            }
        if (defaultNumberThreads==0)
            defaultNumberThreads = Runtime.getRuntime().availableProcessors();
    }

    protected static Task getCurrentTask() {
        return taskMgr_.get();
    }

    protected static void setCurrentTask(Task t) {
        taskMgr_.set(t);
    }

    protected Scheduler() {
    }

    /**
     * create the scheduler with a default queue size
     * @param numThreads the number of threads to use, or use the default if less than zero 
     */
    public Scheduler(int numThreads) {
        this(numThreads,defaultQueueSize_);
    }

    /**
     * create the scheduler
     * @param numThreads the number of threads to use, or use the default if less than zero 
     * @param queueSize the queue size to use
     */
    public Scheduler(int numThreads,int queueSize) {
        if (numThreads < 0)
            numThreads = defaultNumberThreads;
        affinePool_ = new AffineThreadPool(numThreads,queueSize);
    }

    public boolean isEmptyish() {
        return affinePool_.isEmptyish();
    }

    public int numThreads() { return affinePool_.numThreads(); }
        
    /**
     * Schedule a task to run.
     * It is the task's job to ensure that it is not scheduled when it is runnable.
     * the default index for assignment to an executor
     */
    public void schedule(Task t) {
        if (t instanceof RegistrationTask)
            ((RegistrationTask) t).wake();
        else
            schedule(-1,t);
    }

    /**
     * schedule a task to run
     * @param index the index of the executor to use, or less than zero to use the default (round robin) assignment
     * @param t the task
     */
    public void schedule(int index,Task t) {
        if (t instanceof RegistrationTask)
            assert (false);
        else
            affinePool_.publish(index,t);
    }

    public void scheduleTimer(Timer t) {
        affinePool_.scheduleTimer(t);
    }

    /**
     * block the thread till a moment at which all scheduled tasks have completed and then shutdown the scheduler
     * does not prevent scheduling new tasks (from other threads) until the shutdown is complete so such a task
     * could be partially executed
     */
    public void idledown() {
        if (affinePool_!=null&&affinePool_.waitIdle(100))
            shutdown();
    }

    public void shutdown() {
        shutdown.set(true);
        if (defaultScheduler==this)
            defaultScheduler = null;
        if (affinePool_!=null) affinePool_.shutdown();
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    /** a static accessor to allow log to be protected */
    static protected void logRelay(Scheduler sched,Object src,Object obj) { sched.log(src,obj); }
    
    public static interface Logger {
        public void log(Object source,Object problem);
    }
    static class BasicLogger implements Logger {
        public void log(Object source,Object obj) {
            if (obj instanceof Throwable)
                ((Throwable) obj).printStackTrace();
            else
                System.out.println(obj);
        }
    }
    
    /**
     * write to the log
     * @param src the source of the log object
     * @param obj the object to log
     */
    protected void log(Object src,Object obj) {
        if (logger != null)
            logger.log(src,obj);
    }
    
    /**
     * set a logger
     * @param logger the logger
     */
    public void setLogger(Logger logger) { this.logger = logger; }

    public synchronized static Scheduler getDefaultScheduler() {
        if (defaultScheduler==null)
            defaultScheduler = new Scheduler(defaultNumberThreads);
        return defaultScheduler;
    }

    public static void setDefaultScheduler(Scheduler s) {
        defaultScheduler = s;
    }

}



