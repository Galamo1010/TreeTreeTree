package com.example.treamaster.ui.ar

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.treamaster.geometry.Vec3
import com.example.treamaster.measure.Mark
import com.example.treamaster.measure.MarkKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a coloured sphere at each marked point so the user can see where they tapped while walking
 * around. Spheres grow with distance to keep a readable on-screen size. Call every method on the GL thread.
 */
class MarkerRenderer {
    private var program = 0
    private var posLoc = 0
    private var mvpLoc = 0
    private var colorLoc = 0

    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    private val model = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val mvp = FloatArray(16)

    init {
        // Unit sphere; for a unit sphere the position is also the normal.
        val v = ArrayList<Float>()
        for (i in 0..STACKS) {
            val lat = PI * i / STACKS - PI / 2
            for (j in 0..SLICES) {
                val lon = 2 * PI * j / SLICES
                v += (cos(lat) * cos(lon)).toFloat()
                v += sin(lat).toFloat()
                v += (cos(lat) * sin(lon)).toFloat()
            }
        }
        val idx = ArrayList<Short>()
        for (i in 0 until STACKS) {
            for (j in 0 until SLICES) {
                val a = (i * (SLICES + 1) + j).toShort()
                val b = (a + SLICES + 1).toShort()
                idx += listOf(a, b, (a + 1).toShort(), (a + 1).toShort(), b, (b + 1).toShort())
            }
        }
        vertices = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(v.toFloatArray()); position(0) }
        indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(idx.toShortArray()); position(0) }
        indexCount = idx.size
    }

    fun createOnGlThread() {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
        posLoc = GLES20.glGetAttribLocation(program, "a_Position")
        mvpLoc = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        colorLoc = GLES20.glGetUniformLocation(program, "u_Color")
    }

    /** [view] and [projection] are this frame's ARCore camera matrices, [camera] its world position. */
    fun draw(marks: List<Mark>, view: FloatArray, projection: FloatArray, camera: Vec3) {
        if (marks.isEmpty()) return
        Matrix.multiplyMM(viewProj, 0, projection, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(posLoc)
        for (m in marks) {
            val p = m.position
            val radius = max(MIN_RADIUS_M, (p - camera).length() * RADIUS_PER_METRE).toFloat()
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
            Matrix.scaleM(model, 0, radius, radius, radius)
            Matrix.multiplyMM(mvp, 0, viewProj, 0, model, 0)
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
            GLES20.glUniform4fv(colorLoc, 1, color(m.kind), 0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        }
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private companion object {
        const val STACKS = 12
        const val SLICES = 16
        const val MIN_RADIUS_M = 0.03
        // About 0.7 degrees across at any distance.
        const val RADIUS_PER_METRE = 0.006

        val BASE_COLOR = floatArrayOf(0.20f, 0.85f, 0.30f, 1f)
        val TOP_COLOR = floatArrayOf(1.00f, 0.55f, 0.10f, 1f)
        val EDGE_COLOR = floatArrayOf(0.20f, 0.75f, 1.00f, 1f)

        fun color(kind: MarkKind) = when (kind) {
            MarkKind.BASE -> BASE_COLOR
            MarkKind.TOP -> TOP_COLOR
            MarkKind.TRUNK_EDGE -> EDGE_COLOR
        }

        const val VERTEX_SHADER = """
            uniform mat4 u_ModelViewProjection;
            attribute vec4 a_Position;
            varying vec3 v_Normal;
            void main() {
                v_Normal = a_Position.xyz;
                gl_Position = u_ModelViewProjection * a_Position;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            varying vec3 v_Normal;
            void main() {
                float light = max(dot(normalize(v_Normal), normalize(vec3(0.4, 1.0, 0.3))), 0.35);
                gl_FragColor = vec4(u_Color.rgb * light, u_Color.a);
            }
        """
    }
}
