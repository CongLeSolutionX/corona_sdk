//
//  LuaLoader.java
//  TemplateApp
//
//  Copyright (c) 2012 __MyCompanyName__. All rights reserved.
//

// This corresponds to the name of the Lua library,
// e.g. [Lua] require "plugin.testlibrary"
package plugin.testlibrary;

import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;
import com.ansca.corona.CoronaRuntimeTaskDispatcher;
import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.NamedJavaFunction;
import com.adjust.testlibrary.TestLibrary;
import com.adjust.testlibrary.ICommandJsonListener;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the Lua interface for a Corona plugin.
 * <p>
 * Only one instance of this class will be created by Corona for the lifetime of the application.
 * This instance will be re-used for every new Corona activity that gets created.
 */
@SuppressWarnings("WeakerAccess")
public class LuaLoader implements JavaFunction, CoronaRuntimeListener {
	private static final String TAG = "LuaLoader-TestLib";

	/** Lua registry ID to the Lua function to be called when the ad request finishes. */
	private int fListener;

	/** This corresponds to the event name, e.g. [Lua] event.name */
	private static final String EVENT_NAME = "pluginlibraryevent";

	private TestLibrary testLibrary;
	private int executeCommandListener;

	/**
	 * Creates a new Lua interface to this plugin.
	 * <p>
	 * Note that a new LuaLoader instance will not be created for every CoronaActivity instance.
	 * That is, only one instance of this class will be created for the lifetime of the application process.
	 * This gives a plugin the option to do operations in the background while the CoronaActivity is destroyed.
	 */
	@SuppressWarnings("unused")
	public LuaLoader() {
		// Initialize member variables.
		fListener = CoronaLua.REFNIL;
		executeCommandListener = CoronaLua.REFNIL;

		// Set up this plugin to listen for Corona runtime events to be received by methods
		// onLoaded(), onStarted(), onSuspended(), onResumed(), and onExiting().
		CoronaEnvironment.addRuntimeListener(this);
	}

	/**
	 * Called when this plugin is being loaded via the Lua require() function.
	 * <p>
	 * Note that this method will be called every time a new CoronaActivity has been launched.
	 * This means that you'll need to re-initialize this plugin here.
	 * <p>
	 * Warning! This method is not called on the main UI thread.
	 * @param L Reference to the Lua state that the require() function was called from.
	 * @return Returns the number of values that the require() function will return.
	 *         <p>
	 *         Expected to return 1, the library that the require() function is loading.
	 */
	@Override
	public int invoke(LuaState L) {
		// Register this plugin into Lua with the following functions.
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[] {
			new InitWrapper(),
			new ShowWrapper(),
			new InitTestLibraryWrapper(),
			new AddTestWrapper(),
			new AddTestDirectoryWrapper(),
			new StartTestSessionWrapper(),
			new AddInfoToSendWrapper(),
			new SendInfoToServerWrapper(),
			new TestFuncWrapper(),
		};
		String libName = L.toString( 1 );
		L.register(libName, luaFunctions);

		// Returning 1 indicates that the Lua require() function will return the above Lua library.
		return 1;
	}

	/**
	 * Called after the Corona runtime has been created and just before executing the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been loaded/initialized.
	 *                Provides a LuaState object that allows the application to extend the Lua API.
	 */
	@Override
	public void onLoaded(CoronaRuntime runtime) {
		// Note that this method will not be called the first time a Corona activity has been launched.
		// This is because this listener cannot be added to the CoronaEnvironment until after
		// this plugin has been required-in by Lua, which occurs after the onLoaded() event.
		// However, this method will be called when a 2nd Corona activity has been created.

	}

	/**
	 * Called just after the Corona runtime has executed the "main.lua" file.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been started.
	 */
	@Override
	public void onStarted(CoronaRuntime runtime) {
	}

	/**
	 * Called just after the Corona runtime has been suspended which pauses all rendering, audio, timers,
	 * and other Corona related operations. This can happen when another Android activity (ie: window) has
	 * been displayed, when the screen has been powered off, or when the screen lock is shown.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been suspended.
	 */
	@Override
	public void onSuspended(CoronaRuntime runtime) {
	}

	/**
	 * Called just after the Corona runtime has been resumed after a suspend.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that has just been resumed.
	 */
	@Override
	public void onResumed(CoronaRuntime runtime) {
	}

	/**
	 * Called just before the Corona runtime terminates.
	 * <p>
	 * This happens when the Corona activity is being destroyed which happens when the user presses the Back button
	 * on the activity, when the native.requestExit() method is called in Lua, or when the activity's finish()
	 * method is called. This does not mean that the application is exiting.
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param runtime Reference to the CoronaRuntime object that is being terminated.
	 */
	@Override
	public void onExiting(CoronaRuntime runtime) {
		// Remove the Lua listener reference.
		CoronaLua.deleteRef( runtime.getLuaState(), fListener );
		CoronaLua.deleteRef(runtime.getLuaState(), executeCommandListener);

		fListener = CoronaLua.REFNIL;
		executeCommandListener = CoronaLua.REFNIL;
	}

	/**
	 * Simple example on how to dispatch events to Lua. Note that events are dispatched with
	 * Runtime dispatcher. It ensures that Lua is accessed on it's thread to avoid race conditions
	 * @param message simple string to sent to Lua in 'message' field.
	 */
	@SuppressWarnings("unused")
	public void dispatchEvent(final String message) {
		CoronaEnvironment.getCoronaActivity().getRuntimeTaskDispatcher().send( new CoronaRuntimeTask() {
			@Override
			public void executeUsing(CoronaRuntime runtime) {
				LuaState L = runtime.getLuaState();

				CoronaLua.newEvent( L, EVENT_NAME );

				L.pushString(message);
				L.setField(-2, "message");

				try {
					CoronaLua.dispatchEvent( L, fListener, 0 );
				} catch (Exception ignored) {
				}
			}
		} );
	}

	private void dispatchEvent(final LuaState luaState, final int listener, final String name, final String message) {
		CoronaRuntimeTaskDispatcher dispatcher = CoronaEnvironment.getCoronaActivity().getRuntimeTaskDispatcher();
		if (dispatcher == null) {
			return;
		}

		dispatcher.send(new CoronaRuntimeTask() {
			@Override
			public void executeUsing(CoronaRuntime runtime) {
				CoronaLua.newEvent(luaState, name);
				luaState.pushString(message);
				luaState.setField(-2, "message");

				// Dispatch event to library's listener
				try {
					CoronaLua.dispatchEvent(luaState, listener, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * The following Lua function has been called:  library.init( listener )
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param L Reference to the Lua state that the Lua function was called from.
	 * @return Returns the number of values to be returned by the library.init() function.
	 */
	@SuppressWarnings({"WeakerAccess", "SameReturnValue"})
	public int init(LuaState L) {
		int listenerIndex = 1;

		if ( CoronaLua.isListener( L, listenerIndex, EVENT_NAME ) ) {
			fListener = CoronaLua.newRef( L, listenerIndex );
		}

		return 0;
	}

	/**
	 * The following Lua function has been called:  library.show( word )
	 * <p>
	 * Warning! This method is not called on the main thread.
	 * @param L Reference to the Lua state that the Lua function was called from.
	 * @return Returns the number of values to be returned by the library.show() function.
	 */
	@SuppressWarnings("WeakerAccess")
	public int show(LuaState L) {
		// Fetch a reference to the Corona activity.
		// Note: Will be null if the end-user has just backed out of the activity.
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			return 0;
		}

		// Fetch the first argument from the called Lua function.
		String word = L.checkString( 1 );
		if ( null == word ) {
			word = "corona";
		}

		// Create web view on the main UI thread.
		final String url = "http://dictionary.reference.com/browse/" + word;
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// Fetch a reference to the Corona activity.
				// Note: Will be null if the end-user has just backed out of the activity.
				CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
				if (activity == null) {
					return;
				}

				// Create and set up the web view.
				WebView view = new WebView(activity);

				// Prevent redirect which causes an external browser to be launched
				// because some sites detect phone/tablet and redirect.
				view.setWebViewClient(new WebViewClient() {
					@Override
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						return false;
					}
				});

				// Display the web view.
				activity.getOverlayView().addView(view);
				view.loadUrl( url );
			}
		} );


		// This will send event via dispatchEvent in 5 seconds. Not that dispatchEvent is called from
		// background thread, but it will use runtime dispatcher to select proper thread for Lua message dispatch
		(new Thread() {
			@Override
			public void run() {
				// Sleep for 5 seconds
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {}

				// dispatch message!
				dispatchEvent("Hello!");
			}
		}).start();

		return 0;
	}

	/** Implements the library.init() Lua function. */
	@SuppressWarnings("unused")
	private class InitWrapper implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "init";
		}
		
		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			return init(L);
		}
	}

	/** Implements the library.show() Lua function. */
	@SuppressWarnings("unused")
	private class ShowWrapper implements NamedJavaFunction {
		/**
		 * Gets the name of the Lua function as it would appear in the Lua script.
		 * @return Returns the name of the custom Lua function.
		 */
		@Override
		public String getName() {
			return "show";
		}
		
		/**
		 * This method is called when the Lua function is called.
		 * <p>
		 * Warning! This method is not called on the main UI thread.
		 * @param L Reference to the Lua state.
		 *                 Needed to retrieve the Lua function's parameters and to return values back to Lua.
		 * @return Returns the number of values to be returned by the Lua function.
		 */
		@Override
		public int invoke(LuaState L) {
			return show(L);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////
	/////////// TESTAPP CODE //////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////
	public int testLib_initTestLibrary(final LuaState L) {
		Log.d(TAG, "Init test library started...");

		String baseUrl = L.checkString(1);

		if (CoronaLua.isListener(L, 1, "ADJUST")) {
			Log.d(TAG, "Index 1 is listener!");
		}

		if (CoronaLua.isListener(L, 2, "ADJUST")) {
			Log.d(TAG, "Index 2 is listener!");
			this.executeCommandListener = CoronaLua.newRef(L, 2);
		}

		ICommandJsonListener coronaCommandJsonListener = new CoronaCommandJsonListener(new OnExecuteCommandListener() {
			@Override
			public void onExecuteCommand(String className, String methodName, String parameters) {
				Log.d(TAG, "onExecuteCommand: " + className + "." + methodName);

				Map commandMap = new HashMap();
				commandMap.put("className", className);
				commandMap.put("methodName", className);
				commandMap.put("parameters", parameters);

				dispatchEvent(L,
					LuaLoader.this.executeCommandListener,
					"testLibrary_executeCommand",
					new JSONObject(commandMap).toString());
			}
		});

		this.testLibrary = new TestLibrary(baseUrl, coronaCommandJsonListener);

		Log.d(TAG, "Test library init finished.");

		return 0;
	}

	private class CoronaCommandJsonListener implements ICommandJsonListener {
		private OnExecuteCommandListener onExecuteCommandListener;

		CoronaCommandJsonListener(OnExecuteCommandListener onExecuteCommandListener) {
			this.onExecuteCommandListener = onExecuteCommandListener;
		}

		@Override
		public void executeCommand(String className, String methodName, String parameters) {
			onExecuteCommandListener.onExecuteCommand(className, methodName, parameters);
		}
	}

	private interface OnExecuteCommandListener {
		void onExecuteCommand(String className, String methodName, String parameters);
	}

	private int testLib_addTest(LuaState L) {
		String testName = L.checkString(1);
		this.testLibrary.addTest(testName);
		return 0;
	}

	private int testLib_addTestDirectory(LuaState L) {
		String testDirectory = L.checkString(1);
		this.testLibrary.addTestDirectory(testDirectory);
		return 0;
	}

	private int testLib_startTestSession(LuaState L) {
		Log.d(TAG, "StartTestSession called.");

		String clientSdk = L.checkString(1);
		this.testLibrary.startTestSession(clientSdk);
		return 0;
	}

	private int testLib_addInfoToSend(LuaState L) {
		String key = L.checkString(1);
		String value = L.checkString(2);
		this.testLibrary.addInfoToSend(key, value);
		return 0;
	}

	private int testLib_sendInfoToServer(LuaState L) {
		String basePath = L.checkString(1);
		this.testLibrary.sendInfoToServer(basePath);
		return 0;
	}

	private int testLib_testFunc(LuaState L) {
		Log.d(TAG, "Test library test Func called!");
		Log.d(TAG, "Test library test Func called!");
		Log.d(TAG, "Test library test Func called!");
		return 0;
	}

	private class InitTestLibraryWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "initTestLibrary";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_initTestLibrary(L);
		}
	}

	private class AddTestWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "addTest";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_addTest(L);
		}
	}

	private class AddTestDirectoryWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "addTestDirectory";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_addTestDirectory(L);
		}
	}

	private class StartTestSessionWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "startTestSession";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_startTestSession(L);
		}
	}

	private class AddInfoToSendWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "addInfoToSend";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_addInfoToSend(L);
		}
	}

	private class SendInfoToServerWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "sendInfoToServer";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_sendInfoToServer(L);
		}
	}

	private class TestFuncWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "testFunc";
		}

		@Override
		public int invoke(LuaState L) {
			return testLib_testFunc(L);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////
}
