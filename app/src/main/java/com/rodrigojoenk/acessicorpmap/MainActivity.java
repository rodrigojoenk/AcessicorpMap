package com.rodrigojoenk.acessicorpmap;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback, AdapterView.OnItemClickListener {
    public static final int REQUEST_ENABLE_BT = 1;
    public static final int BTLE_SERVICES = 2;
    private boolean aubergineMap = false;
    private boolean removePinAnterior = true;
    private int contadorDeAtualizacoesGPS = 0;
    private int counterDeAtualizacoesRSSI;

    private Locale local_BR = new Locale("PT", "BR"); //Configurando linguagem para o TTS
    private int PERMISSION_ALL = 1;
    private ViewHolder mViewHolder = new ViewHolder(); //Objeto UI que agrupa componentes da interface
    private TextToSpeech mObjetoTTS; //TTS

    private GoogleMap mMap; //Mapa
    private LatLng mLocalAtual; //Meu local atual
    private Marker mMeuMarcador; //Meu marcador
    private List<Address> mEnderecoCompleto;
    private String mEnderecoFormatado;

    private BroadcastReceiver_BTState mBTStateUpdateReceiver;
    private Scanner_BTLE mBLTeScanner;

    private HashMap<String, BTLE_Device> mBTDevicesHashMap;
    private ArrayList<BTLE_Device> mBTDevicesArrayList;
    private ListAdapter_BTLE_Devices adapter;
    private List<String> dispositivosDeInteresse = new ArrayList<>();
    private String dispositivoDeInteresse = "Apresentacao";
    private String distanciaEmMetros;
    private ListView listView;

    private Handler mRepetidor;
    private Runnable mThread;

    //DEBUG PARA MEDIR DBM IPHONE
    private List<Integer> listaBufferRSSI = new ArrayList<>();
    //private Double somatorio = 0.0;

    private static DecimalFormat df2 = new DecimalFormat("#.##");

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
        mEnderecoFormatado = devolveEnderecoFormatado(novaLocalizacao);
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
        //adaptadorBT = BluetoothAdapter.getDefaultAdapter();

        //Testando se suporta Bluetooth LE. Em caso negativo, fecha aplicação.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Utils.toast(getApplicationContext(), "BLE not supported");
            //finish(); //Comentado para funcionar no emulador VM
        }
        //Instancia o receiver de updates de Bluetooth (para fins de debug)
        mBTStateUpdateReceiver = new BroadcastReceiver_BTState(getApplicationContext());
        //Instancia o scanner propriamente. Aqui serão inseridos o tempo de scan e o sinal mínimo requerido
        mBLTeScanner = new Scanner_BTLE(this, 7500, -125); //A mais de 3 metros o valor passa dos -100 RSSI

        //Instancia as listas que receberão os devices
        mBTDevicesHashMap = new HashMap<>();
        mBTDevicesArrayList = new ArrayList<>();

        //Instanciando o adapter do bluetooth
        adapter = new ListAdapter_BTLE_Devices(this, R.layout.btle_device_list_item, mBTDevicesArrayList);

        //List view sendo preparado
        /*listView = new ListView(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        ((ScrollView)findViewById(R.id.scrollView)).addView(listView);
        this.mViewHolder.scrollView = findViewById(R.id.scrollView);*/

        //Rotina que roda a cada 3 segundos, scaneando por devices bluetooth
        mRepetidor = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mBTDevicesArrayList.clear();
                startScan();
                mRepetidor.postDelayed(this, 3000);
            }
        };

        mRepetidor.postDelayed(runnable, 3000); //Rodando pela primeira vez

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
        this.mViewHolder.texto_distancia = findViewById(R.id.textViewDistancia);

        mViewHolder.texto_distancia.setText("Dispositivo de interesse: " + dispositivoDeInteresse);

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
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 3, this);
            //minTime = 0.25 segundos e minDistance = 1 metro

        }   // Se não permite:
        else {
            mViewHolder.campo_texto.setText(getString(R.string.warningGPS));
            Toast.makeText(getApplicationContext(), "Esta aplicação precisa de acesso ao GPS para funcionar corretamente", Toast.LENGTH_SHORT).show();
            System.exit(0);
        }

        //Bloco sobre TTS
        mObjetoTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    mObjetoTTS.setLanguage(local_BR);
                }
            }
        });

        this.mViewHolder.botao = findViewById(R.id.botao); //Botao da tela principal (e listener)
        this.mViewHolder.botao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, "Instrução enviada ao usuário", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                //noinspection deprecation - comentário para remover warning do .speak
                vocalizaString("Dispositivo de interesse: " + dispositivoDeInteresse + ":\nDetectado na distância de: " + distanciaEmMetros + " metros");
                //vocalizaString(mEnderecoFormatado);
                System.out.println("Completo:" + mEnderecoCompleto);
                System.out.println("Formatado:" + mEnderecoFormatado);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mBTStateUpdateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    /*@Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBTStateUpdateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBTStateUpdateReceiver);
        stopScan();
    }*/

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mBTStateUpdateReceiver);
        stopScan();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Utils.toast(getApplicationContext(), "Bluetooth ativado com sucesso");
            }
            else if (resultCode == RESULT_CANCELED) {
                Utils.toast(getApplicationContext(), "Por favor ative o bluetooth");
            }
        }
        else if (requestCode == BTLE_SERVICES) {
            System.out.println();
        }
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

        if (id == R.id.scanBT) {
            if (!mBLTeScanner.isScanning()) {
                startScan();
            }
            else {
                stopScan();
            }
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
        mViewHolder.campo_texto.setText(devolveEnderecoFormatado(location));
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
        //GroundOverlay imagemOverlay = mMap.addGroundOverlay(meuOverlay); //Plota imagem da casa
    }

    private String nomeRua() {
        if (mEnderecoCompleto.get(0).getThoroughfare() == null) {
            String StringACortar = mEnderecoCompleto.get(0).getFeatureName();
            String[] listaFeature = StringACortar.split(",");
            return listaFeature[0];
          }
    else {
            return mEnderecoCompleto.get(0).getThoroughfare();
        }
    }
    private String nomeCidade() {
        return mEnderecoCompleto.get(0).getSubAdminArea();
    }
    private String nomeEstado() {
        return mEnderecoCompleto.get(0).getAdminArea();
    }
    private String nomeLugar() {
        try {
            boolean ehNumero = TextUtils.isDigitsOnly(mEnderecoCompleto.get(0).getFeatureName());
            if(!ehNumero) {
                return mEnderecoCompleto.get(0).getFeatureName().split(",")[0];
            }
        }
        catch (Exception e) {
            return "";
        }
        return "";
    }

    private String devolveEnderecoFormatado(Location novaLocalizacao) {
        Geocoder geocd = new Geocoder(this, local_BR);
        try {
            mEnderecoCompleto = geocd.getFromLocation(novaLocalizacao.getLatitude(), novaLocalizacao.getLongitude(), 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(nomeLugar().equals(nomeRua())) {
            return String.format("Você está na %s. \n %s, %s",  nomeRua(), nomeCidade(), nomeEstado());
        }
        else if(nomeLugar().equals("")) {
            return String.format("Você está na %s, em: \n %s, %s",  nomeRua(), nomeCidade(), nomeEstado());
        }
        else {
            return String.format("Você está em: %s, na %s \n %s, %s", nomeLugar(), nomeRua(), nomeCidade(), nomeEstado());
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        /*Context context = view.getContext();

        Utils.toast(context, "List Item clicked");

        // do something with the text views and start the next activity.

        stopScan();

        String name = mBTDevicesArrayList.get(position).getName();
        String address = mBTDevicesArrayList.get(position).getAddress();

        Intent intent = new Intent(this, Activity_BTLE_Services.class);
        intent.putExtra(Activity_BTLE_Services.EXTRA_NAME, name);
        intent.putExtra(Activity_BTLE_Services.EXTRA_ADDRESS, address);
        startActivityForResult(intent, BTLE_SERVICES);*/
    }

    public void addDevice(BluetoothDevice device, int new_rssi, String new_name) {
        String address = device.getAddress(); //Pega o MAC ADRESS
        if(!mBTDevicesHashMap.containsKey(address)) { //Device sendo registrado pela primeira vez
            BTLE_Device btle_device = new BTLE_Device(device);
            btle_device.setRSSI(new_rssi);

            mBTDevicesHashMap.put(address, btle_device); //Registra no hashmap
            mBTDevicesArrayList.add(btle_device); //Adiciona o device dentro do Array
        }
        else {
            Objects.requireNonNull(mBTDevicesHashMap.get(address)).setRSSI(new_rssi);
            Objects.requireNonNull(mBTDevicesHashMap.get(address)).setName(new_name); //Insere também o nome para o caso de o mesmo ter sido trocado
        }
        adapter.notifyDataSetChanged();
    }

    public void startScan() {
        //mViewHolder.botao.setText(getString(R.string.scanning));

        mBTDevicesArrayList.clear();
        mBTDevicesHashMap.clear();

        adapter.notifyDataSetChanged();

        mBLTeScanner.start();

    }
    public void stopScan() {
        //mViewHolder.botao.setText(getString(R.string.scan_finished));
        verificaDevices();
        mBLTeScanner.stop();

    }

    public void verificaDevices() {
        dispositivosDeInteresse.add("Rodrigo");
        //System.out.println("Foram encontrados " + mBTDevicesArrayList.size() + " dispositivos");
        for(BTLE_Device device:mBTDevicesArrayList) {
            //#DEBUG: Imprime detalhes de cada dispositivo encontrado
//            System.out.println("Nome: " + device.getName());
//            System.out.println("RSSI: " + device.getRSSI());
//            System.out.println("Endereço: " + device.getAddress());
//            System.out.println("->FIM DO OBJETO\n");
            if(device.getName()!=null && device.getName().equals(dispositivoDeInteresse)) {    //Aqui era hardcoded nome do dispositivo, agora pode ser mostrado na interface
                System.out.println("Nome: " + device.getName());
                System.out.println("RSSI: " + device.getRSSI());
                System.out.println("Endereço: " + device.getAddress());
                counterDeAtualizacoesRSSI++;

                if (listaBufferRSSI.size() > 11) {
                    listaBufferRSSI.remove(0);
                    listaBufferRSSI.add(device.getRSSI());

                    ///Código continua

                    System.out.println("MEDIA" + media(listaBufferRSSI));
                    String media = String.format(local_BR, "%.2f" , converteRSSIparaDistancia((int)Math.round(media(listaBufferRSSI))));
                    distanciaEmMetros = media;
                    //vocalizaString("Disância:" + distanciaEmMetros + " metros");

                } else {
                    listaBufferRSSI.add(device.getRSSI());
                }
                System.out.println("ARRAY DE AMOSTRAS RSSI " + listaBufferRSSI);
                System.out.println(counterDeAtualizacoesRSSI);
                //distanciaEmMetros = String.format(local_BR, "%.2f", converteRSSIparaDistancia(device.getRSSI()));
                if(distanciaEmMetros!=null) {
                    mViewHolder.texto_distancia.setText("Dispositivo de interesse: " + dispositivoDeInteresse + ":\nDetectado na distância de: " + distanciaEmMetros + " metros");
                }
                //vocalizaString("Dispositivo " + device.getName() + " RSSI: " + device.getRSSI() + ". Distância de " + distanciaEmMetros + " metros.");

                /*if(device.getName().equals("Rodrigo")) {         //Trecho usado durante o teste de calibração pare medir RSSI em 1 metro
                    amostragens.add(device.getRSSI());
                    somatorio = somatorio+device.getRSSI();
                    System.out.println(">>>>>>>>>");
                    System.out.println("TOTAL" + somatorio);
                    System.out.println("TAMANHO DA LISTA" + amostragens.size());
                    System.out.println("MÉDIA:" + somatorio/amostragens.size());
                    System.out.println("DISTÂNCIA:" + converteRSSIparaDistancia(device.getRSSI()));
                }*/
            }
        }
    }

    public void vocalizaString(String textoVocalizado) {
        mObjetoTTS.speak(textoVocalizado, TextToSpeech.QUEUE_FLUSH, null);
    }

    //RssiAtOneMeter = TxPower - 62. Utilizando -57 como valor médio
    //Distance = 10 ^ ((Measured Power – RSSI)/(10 * N)) N->environmentSignalAtenuation

    //Medição feita com  1 metro e pouca interferência (apenas notebook)
    //TOTAL-2978.0
    //TAMANHO DA LISTA 45
    //MÉDIA:-66.17777777777778

    //Mediçao feita com 1.20 metros e  pouca interferência (geladeira)
    //TOTAL-8636.0
    //TAMANHO DA LISTA123
    //MÉDIA:-70.21138211382114


    public double converteRSSIparaDistancia(int rssi) {
        double potenciaMedida = -68.0; //Valor medido a partir da medição do emissor a 1 metro
        double atenuacaoDeSinalAmbiente = 2.5; //Valor de 2 a 4 que varia conforme ambiente                          /Minha casa = 2.5 // Empresa = 3
        return Math.pow(10, ((potenciaMedida-rssi) / (10*atenuacaoDeSinalAmbiente)));
    }

    private static double media(List<Integer> m) {
        double soma = 0;
        for(int valor:m) {
            soma+= valor;
        }
        return soma/m.size();
    }

//    private static double mediana(List<Integer> m) {
//        List<Integer> temp = m;
//        Collections.sort(temp);
//        int meio = m.size()/2;
//        if (m.size()%2 == 1) {
//            return m.get(meio);
//        } else {
//            return (m.get(meio-1) + m.get(meio)) / 2.0;
//        }
//    }

    //Classe criada para que objetos da view sejam instaciados apenas uma vez e fiquem facilmente acessíveis
    public static class ViewHolder {
        Toolbar toolbar;
        TextView campo_lat;
        TextView campo_long;
        EditText campo_direcao;
        TextView campo_texto;
        Button botao;
        TextView texto_distancia;
        //ScrollView scrollView;
    }
}