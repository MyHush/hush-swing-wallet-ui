// Code was originally written by developer - https://github.com/zlatinb
// Taken from repository https://github.com/zlatinb/zcash-swing-wallet-ui under an MIT license
package com.vaklinov.zcashui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.lang.Exception;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.vaklinov.zcashui.OSUtil.OS_TYPE;
import com.vaklinov.zcashui.ZCashClientCaller.WalletCallException;


public class StartupProgressDialog extends JFrame {
    

    private static final int POLL_PERIOD = 1500;
    private static final int STARTUP_ERROR_CODE = -28;
    
    private BorderLayout borderLayout1 = new BorderLayout();
    private JLabel imageLabel = new JLabel();
    private JLabel progressLabel = new JLabel();
    private JPanel southPanel = new JPanel();
    private BorderLayout southPanelLayout = new BorderLayout();
    private JProgressBar progressBar = new JProgressBar();
    private ImageIcon imageIcon;
    
    private final ZCashClientCaller clientCaller;
    
    public StartupProgressDialog(ZCashClientCaller clientCaller) 
    {
        this.clientCaller = clientCaller;
        
        URL iconUrl = this.getClass().getClassLoader().getResource("images/zdash.png");
        imageIcon = new ImageIcon(iconUrl);
        imageLabel.setIcon(imageIcon);
        imageLabel.setBorder(BorderFactory.createEmptyBorder(32, 32, 0, 32));
        Container contentPane = getContentPane();
        contentPane.setLayout(borderLayout1);
        southPanel.setLayout(southPanelLayout);
        southPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        contentPane.add(imageLabel, BorderLayout.NORTH);
		JLabel zcashWalletLabel = new JLabel(
			"<html><span style=\"font-style:italic;font-weight:bold;font-size:24px\">" + 
		    "HUSH Wallet UI</span></html>");
		zcashWalletLabel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		contentPane.add(zcashWalletLabel, BorderLayout.CENTER);
        contentPane.add(southPanel, BorderLayout.SOUTH);
        progressBar.setIndeterminate(true);
        southPanel.add(progressBar, BorderLayout.NORTH);
        progressLabel.setText("Starting...");
        southPanel.add(progressLabel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
        
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }
    
    public void waitForStartup() throws Exception, IOException,
        InterruptedException,WalletCallException,InvocationTargetException {
        
        // special handling of Windows app launch
        if (OSUtil.getOSType() == OS_TYPE.WINDOWS) 
        {
            ProvingKeyFetcher keyFetcher = new ProvingKeyFetcher();
            keyFetcher.fetchIfMissing(this);
            if ("true".equalsIgnoreCase(System.getProperty("launching.from.appbundle")))
                performWinBundleLaunch();
        }
        
        System.out.println("Splash: checking if hushd is already running...");
        boolean shouldStartZCashd = false;
        try {
            clientCaller.getDaemonRawRuntimeInfo();
        } catch (IOException e) { 
        	// Relying on a general exception may be unreliable
        	// may be thrown for an unexpected reason!!! - so message is checked
        	if (e.getMessage() != null && 
        		e.getMessage().toLowerCase(Locale.ROOT).contains("error: couldn't connect to server"))
        	{
        		shouldStartZCashd = true;
        	}
        }
        
        if (!shouldStartZCashd) {
            System.out.println("Splash: hushd already running...");
            // What if started by hand but taking long to initialize???
//            doDispose();
//            return;
        } else
        {
        	System.out.println("Splash: hushd will be started...");
        }
        
        final Process daemonProcess = 
        	shouldStartZCashd ? clientCaller.startDaemon() : null;
        
        Thread.sleep(POLL_PERIOD); // just a little extra
        
        int iteration = 0;
        while(true) {
        	iteration++;
            Thread.sleep(POLL_PERIOD);
            
            JsonObject info = null;
            
            try
            {
            	info = clientCaller.getDaemonRawRuntimeInfo();
            } catch (IOException e)
            {
            	if (iteration > 4)
            	{
                    setProgressText("Waiting for daemon..." + Integer.toString((40-iteration)));
            	}
                // wait at least 1 minute for daemon to start up
                else if (iteration > 40)
                {
                    throw new Exception("Daemon failed to respond to getDaemonRawRuntimeInfo()", e);
                }
                continue;
            }
            
            JsonValue code = info.get("code");
            if (code == null || (code.asInt() != STARTUP_ERROR_CODE))
                break;
            final String message = info.getString("message", "???");
            setProgressText(message);
            
        }

        // doDispose(); - will be called later by the main GUI
        
        if (daemonProcess != null) // Shutdown only if we started it
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Stopping hushd because we started it - now it is alive: " + 
                		           StartupProgressDialog.this.isAlive(daemonProcess));
                try 
                {
                    clientCaller.stopDaemon();
	                long start = System.currentTimeMillis();
	                
	                while (!StartupProgressDialog.this.waitFor(daemonProcess, 3000))
	                {
	                	long end = System.currentTimeMillis();
	                	System.out.println("Waiting for " + ((end - start) / 1000) + " seconds for hushd to exit...");
	                	
	                	if (end - start > 10 * 1000)
	                	{
	                		clientCaller.stopDaemon();
	                		daemonProcess.destroy();
	                	}
	                	
	                	if (end - start > 1 * 60 * 1000)
	                	{
	                		break;
	                	}
	                }
	            
	                if (StartupProgressDialog.this.isAlive(daemonProcess)) {
	                    	System.out.println("hushd is still alive although we tried to stop it. " +
	                                           "Hopefully it will stop later!");
	                        //System.out.println("hushd is still alive, killing forcefully");
	                        //daemonProcess.destroyForcibly();
	                    } else
	                        System.out.println("hushd shut down successfully");
                } catch (Exception bad) {
                    System.out.println("Couldn't stop hushd!");
                    bad.printStackTrace();
                }
            }
        });
        
    }
    
    public void doDispose() {
        SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setVisible(false);
				dispose();
			}
		});
    }
    
    public void setProgressText(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				progressLabel.setText(text);
			}
	     });
    }
    

    private void performWinBundleLaunch() throws IOException, InterruptedException 
    {
        System.out.println("performing Win Bundle-specific launch");
        String programFiles = System.getenv("PROGRAMFILES");
        File pf = new File(programFiles);
        File bundlePath = new File(pf, "hush4win/app");
        bundlePath = bundlePath.getCanonicalFile();
        
        // run "first-run.bat"
        File firstRun = new File(bundlePath,"first-run.bat");
        if (firstRun.exists())
        {
        	Process firstRunProcess = Runtime.getRuntime().exec(firstRun.getCanonicalPath());
        	firstRunProcess.waitFor();
        }
    }
    
    
    // Custom code - to allow JDK7 compilation.
    public boolean isAlive(Process p) 
    {
    	if (p == null)
    	{
    		return false;
    	}
    	
        try 
        {
            int val = p.exitValue();
            
            return false;
        } catch (IllegalThreadStateException itse) 
        {
            return true;
        }
    }
    
    
    // Custom code - to allow JDK7 compilation.
    public boolean waitFor(Process p, long interval)
    {
		synchronized (this) 
		{
			long startWait = System.currentTimeMillis();
			long endWait = startWait;
			do
			{
				boolean ended = !isAlive(p);
				
				if (ended)
				{
					return true; // End here
				}
				
				try
				{
					this.wait(100);
				} catch (InterruptedException ie)
				{
					// One of the rare cases where we do nothing
					ie.printStackTrace();
				}
				
				endWait = System.currentTimeMillis();
			} while ((endWait - startWait) <= interval);
		}
		
		return false;
    }
}
