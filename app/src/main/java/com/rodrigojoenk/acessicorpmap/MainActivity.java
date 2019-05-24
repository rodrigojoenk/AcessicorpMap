package com.rodrigojoenk.acessicorpmap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {
    boolean aubergineMap = false;
    boolean removePinAnterior = false;
    int contadorDeAtualizacoesGPS = 0;
    public String[] direcoes = {"vire a direita", "vire a esquerda", "siga em frente"};
    int counter = 0; //pode deletar após debug de TT
    Locale local_BR = new Locale("PT", "BR"); //Configurando linguagem para o TTS
    //Criando lista de permissoes a serem concedidas ao aplicativo
    int PERMISSION_ALL = 1;
    String[] PERMISSOES = {
            android.Manifest.permission.ACCESS_COARSE_LOCATION, // Last location para caso GPS esteja com sinal baixo
            android.Manifest.permission.ACCESS_FINE_LOCATION,   // GPS + preciso
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,  // Escrever no armazenamento
            android.Manifest.permission.INTERNET // Acesso a internet
    };

    //Objeto UI
    private ViewHolder mViewHolder = new ViewHolder();
    //TTS
    private TextToSpeech objetoTTS;
    //Mapa
    private GoogleMap mMap;
    //Meu local atual
    private LatLng mLocalAtual;
    //Meu marcador
    Marker mMeuMarcador;


    //Método que testa se permissoes foram dadas:
    public static boolean temPermissoes(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void atualizouGPS(Location novaLocalizacao) {
        //double longitude = novaLocalizacao.getLongitude();
        //double latitude = novaLocalizacao.getLatitude();
        this.mViewHolder.campo_long.setText("" + novaLocalizacao.getLongitude());
        this.mViewHolder.campo_lat.setText("" + novaLocalizacao.getLatitude());
        Toast.makeText(getApplicationContext(), "Parametros de GPS atualizados", Toast.LENGTH_SHORT).show();
    }

    public void limparMapa() {
        mMap.clear();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mViewHolder.toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(this.mViewHolder.toolbar);

        //Parte do mapa
        //MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.mapView);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Referenciando campos de texto de Lat e Long
        this.mViewHolder.campo_lat = findViewById(R.id.editTextLat);
        this.mViewHolder.campo_long = findViewById(R.id.editTextLong);
        this.mViewHolder.campo_direcao = findViewById(R.id.editTextDirecao);
        this.mViewHolder.campo_texto = findViewById(R.id.textoView);

        //Solicitando permissoes definidas em
        if (!temPermissoes(this, PERMISSOES)) {
            ActivityCompat.requestPermissions(this, PERMISSOES, PERMISSION_ALL);
        }

        //Se o usuário permite:
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            //Instancia um loc manager
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            //Registrando serviço que verifica alterações no GPS com parametros de tempo e distancia minima para atualizacao
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 1, this);
            //minTime = 0.25 segundos e minDistance = 1 metro
            //Pega as ultimas coords disponíveis

            /*Location ultima_location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            try {
                double longitude = ultima_location.getLongitude();
                this.mViewHolder.campo_long.setText(longitude + "");
            } catch (Exception e) {
                this.mViewHolder.campo_long.setText(">deu ruim");
            }
            try {
                double latitude = ultima_location.getLatitude();
                this.mViewHolder.campo_lat.setText(latitude + "");
            } catch (Exception e) {
                this.mViewHolder.campo_lat.setText(">deu ruim");
            }
            try {
                String bearing = ultima_location.getProvider();
                this.mViewHolder.campo_direcao.setText(bearing + "");
            } catch (Exception e) {
                this.mViewHolder.campo_direcao.setText(">deu ruim");
            }*/

            //Se não permite:
        } else {
            mViewHolder.campo_texto.setText("Esta aplicação precisa de acesso ao GPS para funcionar corretamente");
            Toast.makeText(getApplicationContext(), "Esta aplicação precisa de acesso ao GPS para funcionar corretamente", Toast.LENGTH_SHORT).show();
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            System.exit(0);
        }


        //Bloco sobre TTS
        objetoTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    objetoTTS.setLanguage(local_BR);
                }
            }
        });

        //Referenciando o botao da tela principal
        this.mViewHolder.botao = findViewById(R.id.botao);
        //Colocando listener e código acionado por ele
        this.mViewHolder.botao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, "Instrução enviada ao usuário", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                mViewHolder.campo_texto.setText(direcoes[counter]);
                objetoTTS.speak(direcoes[counter], TextToSpeech.QUEUE_FLUSH, null);
                counter++;
                if (counter == 3) {
                    counter = 0;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.limpar_mapa) {
            limparMapa();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLocationChanged(Location location) {
        atualizarMapa(location);
        atualizouGPS(location);

    }

    private void atualizarMapa(Location location) {
        if(removePinAnterior) {
            mMeuMarcador.remove();
        }
        mLocalAtual = new LatLng(location.getLatitude(), location.getLongitude());
        System.out.println("Este é o novo local!:" + mLocalAtual);
        mMeuMarcador = mMap.addMarker(new MarkerOptions().position(mLocalAtual).title("Pin n: " + ++contadorDeAtualizacoesGPS));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mLocalAtual, 19));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(getApplicationContext(), "GPS ATIVADO!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(getApplicationContext(), "Por favor ative o GPS!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng minhaCasa = new LatLng(-27.597670, -48.542853);
        mMeuMarcador = mMap.addMarker(new MarkerOptions().position(minhaCasa).title("Pin inicial!"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(minhaCasa, 19));
        if(aubergineMap) {
            boolean sucess = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style));
        }

    }

    //Classe criada para que objetos da view sejam instaciados apenas uma vez
    public static class ViewHolder {
        Toolbar toolbar;
        EditText campo_lat;
        EditText campo_long;
        EditText campo_direcao;
        Button botao;
        TextView campo_texto;
        MenuItem limpar_mapa;
    }
}