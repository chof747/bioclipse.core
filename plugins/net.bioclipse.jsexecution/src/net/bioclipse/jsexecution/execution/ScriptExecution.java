package net.bioclipse.jsexecution.execution;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.util.LogUtils;
import net.bioclipse.jsexecution.Activator;
import net.bioclipse.jsexecution.exceptions.ScriptException;
import net.bioclipse.jsexecution.execution.helper.ThreadSafeConsoleWrap;
import net.bioclipse.jsexecution.tools.ScriptingTools;
import net.bioclipse.managers.MonitorContainer;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.progress.IProgressConstants;

/*
 * This file is part of the Bioclipse JsExecution Plug-in.
 * 
 * Copyright (c) 2008-2009 Johannes Wagener.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Johannes Wagener - initial API and implementation
 */
public class ScriptExecution {

    private static Logger logger = Logger.getLogger( ScriptExecution.class );

    /*
     * This is the only function required by the JsEditor Plug-in
     * The editor Plug-In passes
     * The script as a String
     * The filename of the script
     * A (derived) class/object of MessageConsole that must show
     * the stuff associated with the newly spawned js Context.
     */
    public static void runRhinoScript(String scriptString,
            String scriptDescription,
            MessageConsole parent_console) {

        // The passed console is wrapped with a thread safe API, to make it
        // possible to use it from non-GUI thread.
        // Besides this, the wrap supports simple methods to print with
        // different colors.
        ThreadSafeConsoleWrap console
          = new ThreadSafeConsoleWrap(parent_console);

        // now run the script in a JOB
        runRhinoScriptAsJob(scriptString, scriptDescription, console);
    }

    /*
     * This function prepares a Job that harbors the script when running.
     * TODO: add the code to pass the monitor to spring that it can cancel the
     * script on manager calls.
     */
    private static void runRhinoScriptAsJob(String scriptString,
            String scriptDescription,
            final ThreadSafeConsoleWrap console) {

        final String scriptStringFinal = scriptString;
        final String title = "JavaScript - " + scriptDescription;

        // show progress window		
        IWorkbench wb = PlatformUI.getWorkbench();
        IWorkbenchPage wbPage = wb.getActiveWorkbenchWindow().getActivePage(); 
        if (wbPage != null) {
            IViewPart progressView
              = wbPage.findView("org.eclipse.ui.views.ProgressView");
            if (progressView == null) {
                try {
                    wbPage.showView("org.eclipse.ui.views.ProgressView");
                } catch (PartInitException e) {
                    console.writeToConsole("PartInitException: "
                                           + e.getMessage());
                }
            }
        }
        // define the job
        Job job = new Job(title) {
            private String scriptResult = "undefined";
            protected IStatus run(IProgressMonitor monitor) {
                boolean done = true;

                MonitorContainer.getInstance().addMonitor(
                    SubMonitor.convert(monitor, 100)
                );

                monitor.beginTask( "Running JavaScript...",
                                   IProgressMonitor.UNKNOWN );
                try {
                    monitor.worked(1);
                    scriptResult =
                        runRhinoScript(scriptStringFinal, console, monitor);
                    monitor.done();
                } catch (Exception e) {
                    monitor.setTaskName("Error: " + e.getMessage());
                    logger.debug( e.getMessage(), e );
                    //scriptResult = "test123";
                    scriptResult = e.getMessage();
                    String traced_e = getErrorMessage(e);
                    if (!scriptResult.equals(traced_e))
                        scriptResult = scriptResult
                        + System.getProperty("line.separator")
                        + " " + traced_e;
                    done = false;
                }

                if (done == true)
                    monitor.setTaskName("JavaScript done.");

                if (done == false) {
                    // inform user about error.news
                    setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
                    setProperty(IProgressConstants.ACTION_PROPERTY,
                                JobErrorAction());
                } // ... otherwise, finish job immediately!

                Display.getDefault().syncExec(new Runnable() {
                    public void run() {
                        console.writeToConsole(scriptResult);
                        console.writeToConsoleBlue("JavaScript done.");
                    }
                });
                return Status.OK_STATUS;
            }
            protected Action JobErrorAction() {
                return new Action("JavaScript done") {
                    public void run() {
                        MessageDialog.openError(
                                PlatformUI.getWorkbench()
                                          .getActiveWorkbenchWindow()
                                          .getShell(),
                                title,
                                "The JavaScript script returned an error:\n"
                                + scriptResult);
                    }
                };
            }
        };
        job.setUser(true);
        job.schedule();
    }

    /*
     * This is the method that actually runs the script. It collects the
     * managers and pushes them in the newly created js context. One context
     * per script execution.
     * 
     * Besides this, it creates another object in the context
     * that provides some helper functions located in
     * 
     * <code>net.bioclipse.jsexecution.tools.ScriptingTools</code>
     * 
     * used to pop up a message box, or to make a script sleep for some ms,
     * or to run a runnable in the GUI context.
     * 
     * There is also one helper function that can be used to load an external
     * .jar into the IDE.
     * ( it uses net.bioclipse.jsexecution.tools.JarClasspathLoader.java )
     * 
     */
    private static String runRhinoScript(String scriptString,
            ThreadSafeConsoleWrap console,
            IProgressMonitor monitor) throws ScriptException {
        String scriptResult = "Invalid result.";
        // DO THE ACTUAL EXECUTION OF THE SCRIPT
       /* if (!ContextFactory.hasExplicitGlobal()) {
            ContextFactory.initGlobal(new ContextFactory());
            // THIS IS VERY IMPORTANT!!!
            ContextFactory.getGlobal().initApplicationClassLoader(
                    Activator.class.getClassLoader());
        }*///TODO removed old rhino code
        
        ScriptEngine engine = new ScriptEngineManager( 
                                 Activator.class.getClassLoader() )
                                .getEngineByName( "JavaScript" );
        

        if (engine == null) {
            return "Could not create engine.";
        }

        try {
            // Initialize the standard objects (Object, Function, etc.)
            // This must be done before scripts can be executed. Returns
            // a scope object that we use in later calls.

            ScriptingTools tools;
            tools = new ScriptingTools(console, monitor);

            engine.put( "jst", tools );

            List<Object> managers = Activator.getManagers();
            if (managers != null && managers.size() > 0) {
                Iterator<Object> it = managers.iterator();
                while (it.hasNext() == true) {
                    Object object = it.next();

                    Method method
                      = object.getClass().getDeclaredMethod("getManagerName",
                                                            new Class[0]);
                    //method.setAccessible(true);
                    Object managerName = (String)method.invoke(object);
                    if (managerName instanceof String) {
                        engine.put( (String) managerName, object );
                    }
                }
            }

            
            Object ev = engine.eval( scriptString );

            // Convert the result to a string and print it.
            scriptResult = ev==null?"null":ev.toString();
        } catch (Exception e){
            LogUtils.debugTrace( logger, e );
            throw new ScriptException(e);
        }
        return scriptResult;
    }

    public static String getErrorMessage(Throwable t) {
        if (t == null)
            return "";

        while (!(t instanceof BioclipseException)
                && t.getCause() != null)
            t = t.getCause();

        String msg = t.getMessage();
        if (msg == null)
            msg = "";

        return (t instanceof BioclipseException
                ? "" : t.getClass().getName() + ": ")
                + msg.replaceAll( " end of file",
                " end of line" );
    }
}
