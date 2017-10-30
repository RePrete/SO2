package com.example.raffaele.remotemouse;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements SensorEventListener {
    Socket socket;
    private PrintWriter out;
    private String direction = "none";
    private String ip;
    private int port;
    private Button buttonDestro;
    private Button buttonSinistro;
    private SensorManager mSensorManager = null;
    public static final float EPSILON = 0.000000001f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private float timestamp;
    private boolean initState = true;
    public static final int TIME_CONSTANT = 3;
    public static final float FILTER_COEFFICIENT = 0.985f;
    private Timer fuseTimer = new Timer();
    float initX, initY;

    private boolean firstChange = true;
    // angular speeds from gyro
    private float[] gyro = new float[3];

    // rotation matrix from gyro data
    private float[] gyroMatrix = new float[9];

    // orientation angles from gyro matrix
    private float[] gyroOrientation = new float[3];

    // magnetic field vector
    private float[] magnet = new float[3];

    // accelerometer vector
    private float[] accel = new float[3];

    // orientation angles from accel and magnet
    private float[] accMagOrientation = new float[3];

    // final orientation angles from sensor fusion
    private float[] fusedOrientation = new float[3];

    // accelerometer and magnetometer based rotation matrix
    private float[] rotationMatrix = new float[9];
    private float[] initPosition = new float[3];
    private TextView myText;
    private RadioButton myButton;

    //Thread per la connessione e l'invio periodico dei dati relativi all'invlinazione del dispositivo
    class SocketThread  implements Runnable {
        @Override
        public void run () {
            try {
                socket = new Socket(ip, port);
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                while (true) {
                    //Non invio nulla se l'inclinazione è trascurabile
                    if (!direction.equals("none")) {
                        out.println(direction);
                        out.flush();
                        Thread.sleep(25);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Richiamo la schermata per lo scan del QR code (3rd party)
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        //Setto la modalità di scan per l'applicazione
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        startActivityForResult(intent, 0);
        //Init degli oggetti
        myText = (TextView) findViewById((R.id.text_view1));
        myButton = (RadioButton) findViewById(R.id.radioButton);
        buttonDestro = (Button) findViewById(R.id.right_click);
        buttonSinistro = (Button) findViewById(R.id.left_click);
        //Event listener relativo alla pressione dei tasti a schermo, permette anche di tener premuto il pulsante
        buttonDestro.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    direction = "0rc";
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    direction = "0rcr";
                }
                return false;
            }
        });
        buttonSinistro.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    direction = "0lc";
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    direction = "0lcr";
                }
                return false;
            }
        });
        //Init degli elementi utilizzati per i calcoli relativi ai sensori
        gyroOrientation[0] = 0.0f;
        gyroOrientation[1] = 0.0f;
        gyroOrientation[2] = 0.0f;
        gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
        gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
        gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        initListeners();
        fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(), 1000, TIME_CONSTANT);
    }

    //All'aggiornamento dei sensori effettua i dovuti calcoli e salva il valore relativo all'inclinazione del dispositivoo
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accel, 0, 3);
                calculateAccMagOrientation();
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroFunction(event);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnet, 0, 3);
                break;
        }
        initX = myButton.getTranslationX();
        initY = myButton.getTranslationY();
        if (firstChange == true) {
            System.arraycopy(fusedOrientation, 0, initPosition, 0, 3);
            firstChange = false;
        } else {
            float b = fusedOrientation[1] - initPosition[1];
            float a = fusedOrientation[2] - initPosition[2];
            float incLevel = (float) Math.sqrt(a*a+b*b);
            if (incLevel > 0.33f) {
                Integer offset = 1;
                if (incLevel >= 0.30f && incLevel < 0.40f) offset = 2;
                else if (incLevel >= 0.40f && incLevel < 0.50) offset = 3;
                else if (incLevel >= 0.50f && incLevel < 0.60f) offset = 4;
                else if (incLevel >= 0.60f && incLevel < 0.70f) offset = 5;
                else if (incLevel >= 0.80f && incLevel < 0.90f) offset = 6;
                else offset = 7;
                direction = offset.toString();
                myButton.setChecked(true);
                b *= -1;
                float c = (float) Math.sqrt(Math.abs(a*a)+Math.abs(b*b));
                float angolo = (float) Math.asin(b/c);
                if (a > 0f && b < 0f) {
                    angolo = 6.32f - angolo * -1f;
                } else if (a < 0f && b < 0f) {
                    angolo = angolo * -1f + 3.16f;
                } else if (a < 0f && b > 0f) {
                    angolo = 1.58f - angolo + 1.57f;
                }
                myText.setText(String.format("Angolo: %.2f", incLevel));
                if (angolo >= 5.925 || angolo < 0.395) direction = direction + "nn";
                else if (angolo >= 0.395 && angolo < 1.185) direction = direction + "ne";
                else if (angolo >= 1.185 && angolo < 1.975) direction = direction + "ee";
                else if (angolo >= 1.975 && angolo < 2.765) direction = direction + "se";
                else if (angolo >= 2.765 && angolo < 3.555) direction = direction + "ss";
                else if (angolo >= 3.555 && angolo < 4.345) direction = direction + "so";
                else if (angolo >= 4.345 && angolo < 5.135) direction = direction + "oo";
                else direction = direction + "no";
                myText.append(" " + direction);
            }
            else {
                direction = "none";
                myButton.setChecked(false);
                myText.setText(String.format("Angolo: 0"));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void initListeners(){
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
    }

    class calculateFusedOrientationTask extends TimerTask {
        public void run() {
            float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;
            fusedOrientation[0] =
                    FILTER_COEFFICIENT * gyroOrientation[0]
                            + oneMinusCoeff * accMagOrientation[0];
            fusedOrientation[1] =
                    FILTER_COEFFICIENT * gyroOrientation[1]
                            + oneMinusCoeff * accMagOrientation[1];
            fusedOrientation[2] =
                    FILTER_COEFFICIENT * gyroOrientation[2]
                            + oneMinusCoeff * accMagOrientation[2];
            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);
        }
    }

    public void calibrateSensor (View v) {
        firstChange = true;
    }

    private float[] applyLowPassFilter(float[] input, float[] output) {
        if ( output == null ) return input;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + 0.25f * (input[i] - output[i]);
        }
        return output;
    }

    private float[] matrixMultiplication(float[] A, float[] B) {
        float[] result = new float[9];

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

        return result;
    }

    private float[] getRotationMatrixFromOrientation(float[] o) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float)Math.sin(o[1]);
        float cosX = (float)Math.cos(o[1]);
        float sinY = (float)Math.sin(o[2]);
        float cosY = (float)Math.cos(o[2]);
        float sinZ = (float)Math.sin(o[0]);
        float cosZ = (float)Math.cos(o[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
        xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
        xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
        yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
        yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
        zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
        zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = matrixMultiplication(xM, yM);
        resultMatrix = matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    public void gyroFunction(SensorEvent event) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (accMagOrientation == null)
            return;

        // initialisation of the gyroscope based rotation matrix
        if(initState) {
            float[] initMatrix = new float[9];
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            float[] test = new float[3];
            SensorManager.getOrientation(initMatrix, test);
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
            initState = false;
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        float[] deltaVector = new float[4];
        if(timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp;

        // convert rotation vector into rotation matrix
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);
    }

    private void getRotationVectorFromGyro(float[] gyroValues, float[] deltaRotationVector, float timeFactor) {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude = (float)Math.sqrt(gyroValues[0] * gyroValues[0] + gyroValues[1] * gyroValues[1] + gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if(omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    public void calculateAccMagOrientation() {
        if(SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation);
        }
    }

    //Cosa fare alla chiusura dell'activity per scannerizzare il QR
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                //Richiedo il contenuto dello scan
                String serverDest = intent.getStringExtra("SCAN_RESULT");
                //Divido IP e porta
                String[] parts = serverDest.split(":");
                ip = parts[0];
                port = Integer.parseInt(parts[1]);
                //Avvio il thread che si occupa della connessione e invio periodico dei dati
                new Thread(new SocketThread()).start();
            } else {
                //In caso di uscita precoce dall'attivity per lo scan del QR, la stessa viene riproposta
                System.exit(1);
            }
        }
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        out.close();
    }
}