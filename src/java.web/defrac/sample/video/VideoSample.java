package defrac.sample.video;

import defrac.app.Bootstrap;
import defrac.app.GenericApp;
import defrac.display.*;
import defrac.event.EnterFrameEvent;
import defrac.event.EventBinding;
import defrac.event.EventListener;
import defrac.event.Events;
import defrac.geom.Point;
import defrac.gl.GL;
import defrac.gl.GLTexture;
import defrac.gl.WebGLSubstrate;
import defrac.web.*;

import javax.annotation.Nonnull;
import java.lang.Math;

/**
 *
 */
class VideoSample extends GenericApp {
  static final Window window = Toplevel.window();

  static final int VIDEO_WIDTH = 640;
  static final int VIDEO_HEIGHT = 360;

  static final int VIDEOS_H = 32;
  static final int VIDEOS_V = 32;
  static final int VIDEOS_TOTAL = VIDEOS_V * VIDEOS_H;

  public static void main(final String[] args) {
    Bootstrap.run(new VideoSample());
  }

  TextureData textureData;
  HTMLVideoElement video;
  Image[] videos = new Image[VIDEOS_TOTAL];
  Point[] velocities = new Point[VIDEOS_TOTAL];
  float videoWidth, videoHeight;
  EventBinding<EnterFrameEvent> frameLoop;

  @Override
  protected void onCreate() {
    stage().backgroundColor(0xff000000);

    // Create the video element
    video = (HTMLVideoElement)window.document.createElement("video");

    // Make sure we can play WebM files
    if(video.canPlayType("video/webm").length() == 0) {
      window.alert("Can't play WebM videos :/");
    }

    // Set everything up and start playing the video
    video.autoplay = true;
    video.src = "videoSample/big-buck-bunny_trailer.webm";
    video.onplaying = new defrac.web.EventListener<Event>() {
      @Override
      public void onEvent(Event event) {
        onVideoPlaying();
      }
    };
    video.onended = new defrac.web.EventListener<Event>() {
      @Override
      public void onEvent(Event event) {
        Events.onEnterFrame.remove(frameLoop);
        frameLoop = Events.onEnterFrame.add(new EventListener<EnterFrameEvent>() {
          @Override
          public void onEvent(EnterFrameEvent enterFrameEvent) {
            if (stage().alpha() <= 0.0f) {
              stage().alpha(0.0f);
              Events.onEnterFrame.remove(frameLoop);
            } else {
              stage().alpha(stage().alpha() - 0.01f);
            }
          }
        });
      }
    };
  }

  @Override
  protected void onCreationFailure(@Nonnull Throwable reason) {
    window.alert("It looks like your browser doesn't support WebGL :/");
    super.onCreationFailure(reason);
  }

  private void onVideoPlaying() {
    // Since we want to access methods of WebGLRenderingContext we
    // need to go deep down into the OpenGL dungeon and extract
    // the lowest layer, which is the WebGLRenderingContext

    // The strategy to display video is to use a TextureData and update
    // its contents on each frame.
    //
    // We choose transient because it resides only in VRAM and is therefore
    // perfect for this use case
    textureData = TextureData.Transient.fromFactory(
        new TextureData.Transient.Factory() {
          @Override
          public void texImage2D(@Nonnull final TextureData.Transient textureData,
                                 @Nonnull final GL gl, final int target,
                                 final int level, final int internalFormat, final int border) {
            // The TextureData.Transient.fromFactory method allows us to provide
            // a TextureData that is created on-demand. This call should happen only
            // once but if a context loss occurs we might get asked again.
            //
            // For the web, we want to provide the content of a video as the initial
            // texture. This is why we need access to the WebGLRenderingContext. It
            // allows us to upload the HTMLVideoElement as a texture.
            toWebGL(gl).texImage2D(GL.TEXTURE_2D, 0, GL.RGBA, GL.RGBA, GL.UNSIGNED_BYTE, video);
            try {
              gl.assertNoError();
            } catch (Throwable t) {
              window.alert(t.getMessage());
            }
          }
        },
        VIDEO_WIDTH, VIDEO_HEIGHT,
        TextureDataFormat.RGBA,
        TextureDataRepeat.NO_REPEAT,
        TextureDataSmoothing.LINEAR_WITHOUT_MIPMAP
    );

    // Now that we have a TextureData, we might as well just use it and
    // display it as often as we want.
    Texture texture = new Texture(textureData);
    videoWidth = (1280.0f / (float)VIDEOS_H) * 1.5f;
    videoHeight = (720.0f / (float)VIDEOS_V) * 1.5f;
    int index = 0;
    for(int i = 0; i < VIDEOS_H; ++i) {
      for(int j = 0; j < VIDEOS_V; ++j) {
        Image image = new Image(texture);
        image.
            size(videoWidth, videoHeight).
            moveTo(i * videoWidth, j * videoHeight);
        velocities[index] = new Point(random(), random());
        videos[index++] = image;
        addChild(image);
      }
    }

    frameLoop = Events.onEnterFrame.add(new EventListener<EnterFrameEvent>() {
      @Override
      public void onEvent(EnterFrameEvent enterFrameEvent) {
        onEnterFrame();
      }
    });
  }

  void onEnterFrame() {
    // We want to update the content of the TextureData on each frame.
    // In order to do so we must get a hold of the GLTexture we want to
    // modify first. This is done by asking a TextureScope for the GLTexture
    // associated with a TextureData. Lucky for us, the Stage is a TextureScope
    final GLTexture texture = stage().getOrCreateTexture(textureData);

    // Update the texture
    stage().execGL(new GLBlock() {
      @Override
      public void execGL(@Nonnull GL gl) {
        gl.bindTexture(GL.TEXTURE_2D, texture);
        toWebGL(gl).texSubImage2D(GL.TEXTURE_2D, 0, 0, 0, GL.RGBA, GL.UNSIGNED_BYTE, video);
        gl.bindTexture(GL.TEXTURE_2D, null);
      }
    });

    // Move the videos around ...
    for(int i = 0; i < VIDEOS_TOTAL; ++i) {
      final Image video = videos[i];
      video.moveBy(velocities[i].x, velocities[i].y);

      float vx = video.x();
      float vy = video.y();

      if((vx+videoWidth) > width()) {
        video.x(width() - videoWidth);
        velocities[i].x = -0.1f - (float)Math.random() * 4.0f;
      } else if(vx < 0.0f) {
        video.x(0.0f);
        velocities[i].x = 0.1f + (float)Math.random() * 4.0f;
      }

      if((vy + videoHeight) > height()) {
        video.y(height() - videoHeight);
        velocities[i].y = -0.1f - (float)Math.random() * 4.0f;
      } else if(vy < 0.0f) {
        video.y(0.0f);
        velocities[i].y = 0.1f + (float)Math.random() * 4.0f;
      }
    }
  }

  WebGLRenderingContext toWebGL(GL gl) {
    // Level #1: The platform agnostic and enriched OpenGL interface
    //           is the given parameter

    // Level #2: The substrate is the platform specific
    //           implementation and basis of the enriched API
    //
    //           Note: We know it is a WebGLSubstrate in the case
    //           of the web export and we have to introduce a cast.
    WebGLSubstrate substrate = (WebGLSubstrate)gl.substrate();

    // Level #3: The actual WebGLRenderingContext which the
    //           WebGLSubstrate keeps a reference to. Now we are
    //           looking at the actual WebGL API and can call methods
    //           like texImage2D that take a HTMLVideoElement
    //
    //           The GL object we retrieved in level #1 will make calls
    //           on this object.
    return substrate.context();
  }

  private static float random() {
    return 0.1f + 4.0f * ((float)Math.random() - (float)Math.random());
  }
}
