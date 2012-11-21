package org.andengine.extension.ui.daydream;

import org.andengine.audio.music.MusicManager;
import org.andengine.audio.sound.SoundManager;
import org.andengine.engine.Engine;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.WakeLockOptions;
import org.andengine.entity.scene.Scene;
import org.andengine.extension.ui.livewallpaper.BuildConfig;
import org.andengine.input.sensor.acceleration.AccelerationSensorOptions;
import org.andengine.input.sensor.acceleration.IAccelerationListener;
import org.andengine.input.sensor.location.ILocationListener;
import org.andengine.input.sensor.location.LocationSensorOptions;
import org.andengine.input.sensor.orientation.IOrientationListener;
import org.andengine.input.sensor.orientation.OrientationSensorOptions;
import org.andengine.opengl.font.FontManager;
import org.andengine.opengl.shader.ShaderProgramManager;
import org.andengine.opengl.texture.TextureManager;
import org.andengine.opengl.util.GLState;
import org.andengine.opengl.vbo.VertexBufferObjectManager;
import org.andengine.opengl.view.IRendererListener;
import org.andengine.opengl.view.RenderSurfaceView;
import org.andengine.ui.IGameInterface;
import org.andengine.ui.IGameInterface.OnCreateResourcesCallback;
import org.andengine.ui.IGameInterface.OnCreateSceneCallback;
import org.andengine.ui.IGameInterface.OnPopulateSceneCallback;
import org.andengine.ui.activity.BaseGameActivity;
import org.andengine.util.ActivityUtils;
import org.andengine.util.Constants;
import org.andengine.util.debug.Debug;
import org.andengine.util.system.SystemUtils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout.LayoutParams;

@TargetApi(17)
public abstract class BaseDreamService extends DreamService implements IGameInterface, IRendererListener {
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	protected Engine mEngine;

	protected RenderSurfaceView mRenderSurfaceView;

	private boolean mGamePaused;
	private boolean mGameCreated;
	private boolean mCreateGameCalled;
	private boolean mOnReloadResourcesScheduled;

	// ===========================================================
	// Constructors
	// ===========================================================

	@Override
	public void onCreate() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onCreate" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		super.onCreate();

		this.mGamePaused = true;

		this.mEngine = this.onCreateEngine(this.onCreateEngineOptions());
		this.mEngine.startUpdateThread();

		this.applyEngineOptions();
	}
	
	@Override
	public Engine onCreateEngine(final EngineOptions pEngineOptions) {
		return new Engine(pEngineOptions);
	}
	
	@Override
	public void onAttachedToWindow() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onAttachedToWindow" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}
		super.onAttachedToWindow();
		
		this.onSetContentView();
	}
	
	@Override
	public synchronized void onSurfaceChanged(final GLState pGLState, final int pWidth, final int pHeight) {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onSurfaceChanged(Width=" + pWidth + ",  Height=" + pHeight + ")" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}
	}
	
	@Override
	public synchronized void onGameCreated() {
		this.mGameCreated = true;

		/* Since the potential asynchronous resource creation,
		 * the surface might already be invalid
		 * and a resource reloading might be necessary. */
		if(this.mOnReloadResourcesScheduled) {
			this.mOnReloadResourcesScheduled = false;
			try {
				this.onReloadResources();
			} catch(final Throwable pThrowable) {
				Debug.e(this.getClass().getSimpleName() + ".onReloadResources failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
			}
		}
	}
	
	@Override
	public void onDreamingStarted() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onDreamingStarted" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		super.onDreamingStarted();

		this.mRenderSurfaceView.onResume();
	}
	
	@Override
	public synchronized void onSurfaceCreated(final GLState pGLState) {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onSurfaceCreated" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		if(this.mGameCreated) {
			this.onReloadResources();

			if(this.mGamePaused && this.mGameCreated) {
				this.onResumeGame();
			}
		} else {
			if(this.mCreateGameCalled) {
				this.mOnReloadResourcesScheduled = true;
			} else {
				this.mCreateGameCalled = true;
				this.onCreateGame();
			}
		}
	}
	
	protected synchronized void onCreateGame() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onCreateGame" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		final OnPopulateSceneCallback onPopulateSceneCallback = new OnPopulateSceneCallback() {
			@Override
			public void onPopulateSceneFinished() {
				try {
					if(BuildConfig.DEBUG) {
						Debug.d(BaseDreamService.this.getClass().getSimpleName() + ".onGameCreated" + " @(Thread: '" + Thread.currentThread().getName() + "')");
					}

					BaseDreamService.this.onGameCreated();
				} catch(final Throwable pThrowable) {
					Debug.e(BaseDreamService.this.getClass().getSimpleName() + ".onGameCreated failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
				}

				BaseDreamService.this.callGameResumedOnUIThread();
			}
		};

		final OnCreateSceneCallback onCreateSceneCallback = new OnCreateSceneCallback() {
			@Override
			public void onCreateSceneFinished(final Scene pScene) {
				BaseDreamService.this.mEngine.setScene(pScene);

				try {
					if(BuildConfig.DEBUG) {
						Debug.d(BaseDreamService.this.getClass().getSimpleName() + ".onPopulateScene" + " @(Thread: '" + Thread.currentThread().getName() + "')");
					}

					BaseDreamService.this.onPopulateScene(pScene, onPopulateSceneCallback);
				} catch(final Throwable pThrowable) {
					Debug.e(BaseDreamService.this.getClass().getSimpleName() + ".onPopulateScene failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
				}
			}
		};

		final OnCreateResourcesCallback onCreateResourcesCallback = new OnCreateResourcesCallback() {
			@Override
			public void onCreateResourcesFinished() {
				try {
					if(BuildConfig.DEBUG) {
						Debug.d(BaseDreamService.this.getClass().getSimpleName() + ".onCreateScene" + " @(Thread: '" + Thread.currentThread().getName() + "')");
					}

					BaseDreamService.this.onCreateScene(onCreateSceneCallback);
				} catch(final Throwable pThrowable) {
					Debug.e(BaseDreamService.this.getClass().getSimpleName() + ".onCreateScene failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
				}
			}
		};

		try {
			if(BuildConfig.DEBUG) {
				Debug.d(this.getClass().getSimpleName() + ".onCreateResources" + " @(Thread: '" + Thread.currentThread().getName() + "')");
			}

			this.onCreateResources(onCreateResourcesCallback);
		} catch(final Throwable pThrowable) {
			Debug.e(this.getClass().getSimpleName() + ".onCreateGame failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
		}
	}
	

	@Override
	public synchronized void onResumeGame() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onResumeGame" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		this.mEngine.start();

		this.mGamePaused = false;
	}

	@Override
	public void onReloadResources() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onReloadResources" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		this.mEngine.onReloadResources();
	}

	@Override
	public void onDreamingStopped() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onDreamingStopped" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		super.onDreamingStopped();

		this.mRenderSurfaceView.onPause();

		if(!this.mGamePaused) {
			this.onPauseGame();
		}
	}
	
	@Override
	public synchronized void onPauseGame() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onPauseGame" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		this.mGamePaused = true;

		this.mEngine.stop();
	}

	@Override
	public void onDetachedFromWindow() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onDetachedFromWindow" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onDestroy() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onDestroy" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		super.onDestroy();

		this.mEngine.onDestroy();

		try {
			this.onDestroyResources();
		} catch (final Throwable pThrowable) {
			Debug.e(this.getClass().getSimpleName() + ".onDestroyResources failed." + " @(Thread: '" + Thread.currentThread().getName() + "')", pThrowable);
		}

		this.onGameDestroyed();

		this.mEngine = null;
	}

	@Override
	public void onDestroyResources() throws Exception {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onDestroyResources" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		if(this.mEngine.getEngineOptions().getAudioOptions().needsMusic()) {
			this.getMusicManager().releaseAll();
		}

		if(this.mEngine.getEngineOptions().getAudioOptions().needsSound()) {
			this.getSoundManager().releaseAll();
		}
	}

	@Override
	public synchronized void onGameDestroyed() {
		if(BuildConfig.DEBUG) {
			Debug.d(this.getClass().getSimpleName() + ".onGameDestroyed" + " @(Thread: '" + Thread.currentThread().getName() + "')");
		}

		this.mGameCreated = false;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public Engine getEngine() {
		return this.mEngine;
	}

	public boolean isGamePaused() {
		return this.mGamePaused;
	}

	public boolean isGameRunning() {
		return !this.mGamePaused;
	}

	public boolean isGameLoaded() {
		return this.mGameCreated;
	}

	public VertexBufferObjectManager getVertexBufferObjectManager() {
		return this.mEngine.getVertexBufferObjectManager();
	}

	public TextureManager getTextureManager() {
		return this.mEngine.getTextureManager();
	}

	public FontManager getFontManager() {
		return this.mEngine.getFontManager();
	}

	public ShaderProgramManager getShaderProgramManager() {
		return this.mEngine.getShaderProgramManager();
	}

	public SoundManager getSoundManager() {
		return this.mEngine.getSoundManager();
	}

	public MusicManager getMusicManager() {
		return this.mEngine.getMusicManager();
	}
	
	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================

	private void callGameResumedOnUIThread() {
		this.onResumeGame();
//		runOnUiThread(new Runnable() {
//			@Override
//			public void run() {
//				BaseGameActivity.this.onResumeGame();
//			}
//		});
	}

	protected void onSetContentView() {
		this.mRenderSurfaceView = new RenderSurfaceView(this);
		this.mRenderSurfaceView.setRenderer(this.mEngine, this);

		this.setContentView(this.mRenderSurfaceView, createSurfaceViewLayoutParams());
	}

	/**
	 * @see Engine#runOnUpdateThread(Runnable)
	 */
	public void runOnUpdateThread(final Runnable pRunnable) {
		this.mEngine.runOnUpdateThread(pRunnable);
	}

	/**
	 * @see Engine#runOnUpdateThread(Runnable, boolean)
	 */
	public void runOnUpdateThread(final Runnable pRunnable, final boolean pOnlyWhenEngineRunning) {
		this.mEngine.runOnUpdateThread(pRunnable, pOnlyWhenEngineRunning);
	}

	private void applyEngineOptions() {
		final EngineOptions engineOptions = this.mEngine.getEngineOptions();

		setFullscreen(engineOptions.isFullscreen());

		if(engineOptions.getAudioOptions().needsMusic() || engineOptions.getAudioOptions().needsSound()) {
			getWindow().setVolumeControlStream(AudioManager.STREAM_MUSIC);
		}
	}

	protected static LayoutParams createSurfaceViewLayoutParams() {
		final LayoutParams layoutParams = new LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
		layoutParams.gravity = Gravity.CENTER;
		return layoutParams;
	}

	protected void enableVibrator() {
		this.mEngine.enableVibrator(this);
	}

	/**
	 * @see {@link Engine#enableLocationSensor(Context, ILocationListener, LocationSensorOptions)}
	 */
	protected void enableLocationSensor(final ILocationListener pLocationListener, final LocationSensorOptions pLocationSensorOptions) {
		this.mEngine.enableLocationSensor(this, pLocationListener, pLocationSensorOptions);
	}

	/**
	 * @see {@link Engine#disableLocationSensor(Context)}
	 */
	protected void disableLocationSensor() {
		this.mEngine.disableLocationSensor(this);
	}

	/**
	 * @see {@link Engine#enableAccelerationSensor(Context, IAccelerationListener)}
	 */
	protected boolean enableAccelerationSensor(final IAccelerationListener pAccelerationListener) {
		return this.mEngine.enableAccelerationSensor(this, pAccelerationListener);
	}

	/**
	 * @see {@link Engine#enableAccelerationSensor(Context, IAccelerationListener, AccelerationSensorOptions)}
	 */
	protected boolean enableAccelerationSensor(final IAccelerationListener pAccelerationListener, final AccelerationSensorOptions pAccelerationSensorOptions) {
		return this.mEngine.enableAccelerationSensor(this, pAccelerationListener, pAccelerationSensorOptions);
	}

	/**
	 * @see {@link Engine#disableAccelerationSensor(Context)}
	 */
	protected boolean disableAccelerationSensor() {
		return this.mEngine.disableAccelerationSensor(this);
	}

	/**
	 * @see {@link Engine#enableOrientationSensor(Context, IOrientationListener)}
	 */
	protected boolean enableOrientationSensor(final IOrientationListener pOrientationListener) {
		return this.mEngine.enableOrientationSensor(this, pOrientationListener);
	}

	/**
	 * @see {@link Engine#enableOrientationSensor(Context, IOrientationListener, OrientationSensorOptions)}
	 */
	protected boolean enableOrientationSensor(final IOrientationListener pOrientationListener, final OrientationSensorOptions pLocationSensorOptions) {
		return this.mEngine.enableOrientationSensor(this, pOrientationListener, pLocationSensorOptions);
	}

	/**
	 * @see {@link Engine#disableOrientationSensor(Context)}
	 */
	protected boolean disableOrientationSensor() {
		return this.mEngine.disableOrientationSensor(this);
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
