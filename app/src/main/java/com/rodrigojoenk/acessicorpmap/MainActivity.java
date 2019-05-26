package com.rodrigojoenk.acessicorpmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {
    private boolean aubergineMap = false;
    private boolean removePinAnterior = true;
    private int contadorDeAtualizacoesGPS = 0;
    private List<Address> mEnderecoCompleto;
    private BluetoothAdapter adaptadorBT;
    private Locale local_BR = new Locale("PT", "BR"); //Configurando linguagem para o TTS
    private int PERMISSION_ALL = 1;
    private ViewHolder mViewHolder = new ViewHolder(); //Objeto UI que agrupa componentes da interface
    private TextToSpeech objetoTTS; //TTS
    private GoogleMap mMap; //Mapa
    private LatLng mLocalAtual; //Meu local atual
    private Marker mMeuMarcador; //Meu marcador
    private Geocoder mMeuGeoCoder;
    private String[] PERMISSOES = {   //Criando lista de permissoes a serem concedidas ao aplicativo
            Manifest.permission.ACCESS_COARSE_LOCATION, // Last location para caso GPS esteja com sinal baixo
            Manifest.permission.ACCESS_FINE_LOCATION,   // GPS + preciso
            Manifest.permission.WRITE_EXTERNAL_STORAGE,  // Escrever no armazenamento
            Manifest.permission.INTERNET, // Acesso a internet
            Manifest.permission.BLUETOOTH, // Bluetooth
            Manifest.permission.BLUETOOTH_ADMIN, //Bluetooth
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

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
        Geocoder geocd = new Geocoder(this, local_BR);
        try {
            mEnderecoCompleto = geocd.getFromLocation(novaLocalizacao.getLatitude(), novaLocalizacao.getLongitude(), 1);
                System.out.println(mEnderecoCompleto);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.mViewHolder.campo_long.setText(String.format("%s", novaLocalizacao.getLongitude()));
        this.mViewHolder.campo_lat.setText(String.format("%s", novaLocalizacao.getLatitude()));
        Toast.makeText(getApplicationContext(), "Parametros de GPS atualizados", Toast.LENGTH_SHORT).show();
    }

    public void limparMapa() { //Limpar marcadores e layers adicionados
        mMap.clear();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mViewHolder.toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(this.mViewHolder.toolbar);
        adaptadorBT = BluetoothAdapter.getDefaultAdapter();

        //Inicializando mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        //Referenciando componentes de UI e adicionando ao controlador mViewHolder
        this.mViewHolder.campo_lat = findViewById(R.id.editTextLat);
        this.mViewHolder.campo_long = findViewById(R.id.editTextLong);
        this.mViewHolder.campo_direcao = findViewById(R.id.editTextDirecao);
        this.mViewHolder.campo_texto = findViewById(R.id.textoView);

        //Solicitando permissoes definidas
        if (!temPermissoes(this, PERMISSOES)) {
            ActivityCompat.requestPermissions(this, PERMISSOES, PERMISSION_ALL);
        }

        //Se o usuário permite:
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            //Instancia um loc manager
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            //Registrando serviço que verifica alterações no GPS com parametros de tempo e distancia minima para atualizacao
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 150, 1, this);
            //minTime = 0.25 segundos e minDistance = 1 metro

        }   // Se não permite:
        else {
            mViewHolder.campo_texto.setText(getString(R.string.warningGPS));
            Toast.makeText(getApplicationContext(), "Esta aplicação precisa de acesso ao GPS para funcionar corretamente", Toast.LENGTH_SHORT).show();
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

        this.mViewHolder.botao = findViewById(R.id.botao); //Botao da tela principal (e listener)
        this.mViewHolder.botao.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                Snackbar.make(v, "Instrução enviada ao usuário", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                mViewHolder.campo_texto.setText("Você está na rua " + nomeRua() +" em: \n Cidade: " + nomeCidade() + " - Estado: " + nomeEstado());
                //noinspection deprecation - comentário para remover warning do .speak
                objetoTTS.speak("Você está na " + nomeRua() +". em " + nomeCidade() + ", " + nomeEstado(), TextToSpeech.QUEUE_FLUSH, null);
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
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mLocalAtual, 20));
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
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(minhaCasa, 20));
        if(aubergineMap) {
            boolean sucess = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style));
        }
        GroundOverlayOptions meuOverlay = new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(R.drawable.myhouse))
                .position(minhaCasa, 15)
                .bearing(-20);
        GroundOverlay imagemOverlay = mMap.addGroundOverlay(meuOverlay);
    }

    private String nomeRua() {
        return mEnderecoCompleto.get(0).getThoroughfare();
    }

    private String nomeCidade() {
        return mEnderecoCompleto.get(0).getSubAdminArea();
    }

    private String nomeEstado() {
        return mEnderecoCompleto.get(0).getAdminArea();
    }


    //Classe criada para que objetos da view sejam instaciados apenas uma vez
    public static class ViewHolder {
        Toolbar toolbar;
        EditText campo_lat;
        EditText campo_long;
        EditText campo_direcao;
        TextView campo_texto;
        Button botao;
    }
}