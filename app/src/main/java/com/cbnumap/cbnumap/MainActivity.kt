package com.cbnumap.cbnumap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.cbnumap.cbnumap.databinding.ActivityMainBinding
import com.cbnumap.cbnumap.server.Coordinate
import com.cbnumap.cbnumap.server.CoordinateDB
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var polyLine: Polyline

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var coarseLocation = 1001
    private var fineLocation = 1002

    //room db 관련된 부분
    private var coordinateDB: CoordinateDB? = null
    private var coordinateList = listOf<Coordinate>()

    //빠른 길 찾기 부분
    data class Temp(val startId: Int, val endId: Int, val weight: Int) : Comparable<Temp> {
        override fun compareTo(other: Temp): Int = weight - other.weight
    }

    //빠른 길 찾기 부분
    private var pointList = Array(99999) { Stack<Temp>() }
    private val pq = PriorityQueue<Temp>() // 다익스트라 알고리즘 위한 priority queue
    private val dist = IntArray(99999) { Integer.MAX_VALUE - 1 } // 최댓값으로 구성
    private val from = IntArray(99999) { -1 } // 이전 점(위치)를 저장하기 위한 배열
    private val finalPath = mutableListOf<Int>()

    //뷰가 화면에 보이는지 여부
    private var cameDown =
        false // camedown이 false면 화면이 내려와있지 않은 상태고, camedown이 true면 화면이 내려와 있는 상태다.
    private var predictedTimeVisible = false // false면 예상 소요시간 레이아웃이 보이지 않는 상태고, true면 예상 소요시간 레이아웃이 보이는 상태입니다.

    //routeInfoLinearLayout이 얼마나 놔와야 하는지 구하기 위한 width
    private var routeLayoutWidth = 0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //키보드가 아래서 위로 올라올 때 레이아웃도 같이 딸려 올라가는 것을 방지
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        // Get the SupportMapFragment and request notification when the map is ready to be used.
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        val screenHeight = getHeight(baseContext)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //권한 요청하는 부분
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                fineLocation
            )
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocation
            )
            return
        }


        val upSize = (screenHeight.toFloat() / 7f) // 화면 중 1/7만큼을 차지하는 윗 부분
        val downSize = (screenHeight.toFloat() / 7f) * 6f // 화면 중 6/7만큼을 차지하는 아래 부분
        binding.comeDownBtn.setOnClickListener {
            Log.e("comeDownBtn", "Clicked!")
            binding.comeDownBtn.isClickable = false //뷰에 가려지기 때문에 클릭 못하도록 설정

            val upLayout = binding.upLinear//위에서 아래로 내려오는 LinearLayout
            val upParams = upLayout.layoutParams
            upParams.height = upSize.toInt() // layout height를 내려올 만큼으로 바꿈

            val downLayout = binding.downLinear // 아래에서 위로 올라오는 LinearLayout
            val downParams = downLayout.layoutParams
            downParams.height = downSize.toInt() // layout height를 내려올 만큼으로 바꿈
            Log.e("screenHeight", screenHeight.toString())
            Log.e("upSize", upSize.toString())
            Log.e("downSize", downSize.toString())

            // 길 찾기 하는 상황에서 예상 소요시간은 보이면 안되므로 다시 집어 넣음.
            if(predictedTimeVisible){
                binding.routeInfoLinearLayout.animate().translationX(routeLayoutWidth).withLayer().duration =
                    250
                predictedTimeVisible = false
            }

            if (!cameDown) {
                binding.upLinear.animate().translationY(upSize).withLayer().duration = 500
                binding.comeDownBtn.text = "길찾기" // 이상하게 이 부분을 삭제하면 안됨
                binding.downLinear.animate().translationY(-downSize).withLayer().duration = 500
                cameDown = true
            } else {
                binding.upLinear.animate().translationY(-upSize).withLayer().duration = 500
                binding.downLinear.animate().translationY(downSize).withLayer().duration = 500
                //binding.comeDownBtn.text = "COME DOWN"
                cameDown = false
            }
        }

        CoroutineScope(Dispatchers.Unconfined).launch {
            //RoomDB에서 데이터를 가져오는 부분
            coordinateDB = CoordinateDB.getInstance(applicationContext)
            val r = Runnable {
                // 빈 list로 초기화된 coordinateList에 Room db에 저장된 정보를 모두 읽어와서
                // Coordinate의 형태로 저장한다. getAll() 메서드를 통해 가져온다.
                // SubThread를 이용해서 Main Thread에 영향을 주지 않도록 해야 한다.
                coordinateList = coordinateDB?.coordinateDao()?.getAll()!!
                Log.e("kor_name", coordinateList[0].kor_name)
                Log.e("latitude", coordinateList[0].latitude.toString())
                Log.e("longitude", coordinateList[0].longitude.toString())
                Log.e("id", coordinateList[0].id.toString())
            }
            val thread = Thread(r)
            thread.start()
            thread.join()
        }


        /** autoCompleteText 부분 **/
        // 자동완성 창에 지명 넣기 위해 autoTextStringList 생성
        val autoTextStringList = mutableListOf<String>()
        for (i in coordinateList) {
            if (i.kor_name.isNotEmpty()) {
                autoTextStringList.add(i.kor_name) // 지명 이름 추가
            }
            if (i.building_id.isNotEmpty()) {
                autoTextStringList.add(i.building_id)
            }
        }
        println("@@@@ ${coordinateList[1].kor_name}")

        println("@@@@ $autoTextStringList")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, autoTextStringList)
        val startPosATV = findViewById<AutoCompleteTextView>(R.id.startAutoTV)
        val endPosATV = findViewById<AutoCompleteTextView>(R.id.endAutoTV)
        startPosATV.setAdapter(adapter)
        startPosATV.threshold = 1
        endPosATV.setAdapter(adapter)
        endPosATV.threshold = 1

        /** 사용자가 값을 입력하면 그 즉시 다익스트라로 위치를 찾는다. **/
        var startPosString = "" // 사용자가 입력한 출발점
        var endPosString = "" // 사용자가 입력한 도착점
        //사용자가 모두 입력했을 때 둘 다 true로 바뀌고 findpath를한다.
        //모두 입력했으면 다시 입력할 경우를 대비하여 false로 바꿔놓는다.
        var startPosInputted = false // 사용자가 출발점을 입력했는지 확인하기 위함
        var endPosInputted = false // 사용자가 도착점을 입력했는지 확인하기 위함
        var startPosId = -1 // 출발점 id, id 기준은 Room db 기준
        var endPosId = -1 // 도착점 id

        //출발 지점이 다 입력되었으면 처리할 곳
        //item이 눌러졌을 때 처리
        startPosATV.setOnItemClickListener { adapterView, view, position, id ->
            startPosString = startPosATV.text.toString()
            val routeStr = SpannableString("${startPosString}에서부터 ${endPosString}까지의 경로를 찾습니다.")
            routeStr.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                startPosString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                routeStr.setSpan(
                    ForegroundColorSpan(applicationContext.getColor(R.color.crimson)),
                    0,
                    startPosString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            routeStr.setSpan(
                StyleSpan(Typeface.BOLD),
                startPosString.length + 5,
                startPosString.length + endPosString.length + 5,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                routeStr.setSpan(
                    ForegroundColorSpan(applicationContext.getColor(R.color.crimson)),
                    startPosString.length + 5,
                    startPosString.length + endPosString.length + 5,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.findRouteText.text = routeStr
            //binding.findRouteText.text = "${startPosString}에서부터 ${endPosString}까지의 경로를 찾습니다."
            for (i in coordinateList) {
                // 사용자가 건물명(kor_name)을 넣었다면 건물명 중에 일치하는 값이 있는지 비교
                if (i.kor_name == startPosString) {
                    startPosInputted = true
                    startPosId = i.id
                    break // 원하는 값을 찾았으므로 탈출
                }
                // 사용자가 빌딩 id(building_id)를 넣었다면 building_id 중에 일치하는 값이 있는지 비교
                if (i.building_id == startPosString) {
                    startPosInputted = true
                    startPosId = i.id
                    break // 원하는 값을 찾았으므로 탈출
                }
            }
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }

        //도착지가 다 입력되었으면 처리할 곳
        endPosATV.setOnItemClickListener { adapterView, view, position, id ->
            endPosString = endPosATV.text.toString()
            val routeStr = SpannableString("${startPosString}에서부터 ${endPosString}까지의 경로를 찾습니다.")
            routeStr.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                startPosString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                routeStr.setSpan(
                    ForegroundColorSpan(applicationContext.getColor(R.color.crimson)),
                    0,
                    startPosString.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            routeStr.setSpan(
                StyleSpan(Typeface.BOLD),
                startPosString.length + 5,
                startPosString.length + endPosString.length + 5,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                routeStr.setSpan(
                    ForegroundColorSpan(applicationContext.getColor(R.color.crimson)),
                    startPosString.length + 5,
                    startPosString.length + endPosString.length + 5,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.findRouteText.text = routeStr
            for (i in coordinateList) {
                // 사용자가 건물명(kor_name)을 넣었다면 건물명 중에 일치하는 값이 있는지 비교
                if (i.kor_name == endPosString) {
                    endPosInputted = true
                    endPosId = i.id
                    break // 원하는 값을 찾았으므로 탈출
                }
                // 사용자가 빌딩 id(building_id)를 넣었다면 building_id 중에 일치하는 값이 있는지 비교
                if (i.building_id == endPosString) {
                    endPosInputted = true
                    endPosId = i.id
                    break // 원하는 값을 찾았으므로 탈출
                }
            }
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }

        binding.searchRouteBtn.setOnClickListener {
            if (startPosInputted && endPosInputted) {
                mMap.clear() // 기존에 그려져 있던 라인들을 지우고 다시 findpath를 한다.
                if(startPosString == endPosString){
                    Toast.makeText(baseContext, "시작점과 도착점이 같습니다.\n다시 확인해주세요.", Toast.LENGTH_SHORT).show()
                }else{
                    findPath(startPosId, endPosId)
                    binding.comeDownBtn.isClickable = true // 다시 뷰에 보이기 때문에 클릭 가능하게 설정
                    Log.d("comeDownBtn", "true")
                    binding.upLinear.animate().translationY(-upSize).withLayer().duration = 250
                    binding.downLinear.animate().translationY(downSize).withLayer().duration = 250

                    cameDown = false
                    startPosATV.setText("")
                    endPosATV.setText("")
                    binding.findRouteText.text = "경로를 입력해주세요!"


                    Log.e("endPosId", dist[endPosId].toString())
                    Log.e("end weight sum", (dist[endPosId] / 4000 * 60).toString())
                    binding.predictTimeTextView.text =
                        "예상 소요시간 : ${(dist[endPosId].toFloat() / 3250F * 60F).toInt()}분" // 걷는 속도를 시속 3.25km로 잡음.
                    routeLayoutWidth = binding.predictTimeTextView.width.toFloat()
                    binding.routeInfoLinearLayout.animate().translationX(-routeLayoutWidth).withLayer().duration =
                        250
                    predictedTimeVisible = true // routeInfoLinearLayout이 다시 보이는 쪽으로 나왔으므로 예상 소요시간을 보여준다.
                    startPosString = ""
                    endPosString = ""
                    startPosInputted = false
                    endPosInputted = false
                    startPosId = -1
                    endPosId = -1
                }
            } else if (startPosInputted && !endPosInputted) {
                Toast.makeText(baseContext, "도착점을 입력해주세요!", Toast.LENGTH_SHORT).show()
            } else if (!startPosInputted && endPosInputted) {
                Toast.makeText(baseContext, "시작점을 입력해주세요!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(baseContext, "도착점과 시작점을 입력해주세요!", Toast.LENGTH_SHORT).show()
            }
        }

        addWeight()
    }

    /**
     * 경로를 찾는 함수입니다.
     * 경로를 찾아서 from 배열에 저장하여 역추적합니다.
     * startId는 시작점이고, endId는 도착점입니다.
     **/
    private fun findPath(startId: Int, endId: Int) {
        // 다시 검색할 때는 초기화해줘야 한다.
        // finalpath.clear()까지는 초기화 하는 부분
        pq.clear()
        for (i in dist.indices) {
            dist[i] = Integer.MAX_VALUE - 1
            from[i] = -1
        }
        finalPath.clear()

        Log.d("find Path", "find Path In")
        //임시적으로 양성재에서 출발해서 소웨과동까지 가는 것 구현해봄
        //pq.add(Temp(4, 7, 53)) // 출발점 pq에 입력
        for (i in pointList[startId]) {
            pq.add(Temp(i.startId, i.endId, i.weight)) // 시작점으로부터 연결된 노드들을 모두 더해서 초기화한다.
        }
        //dist[4] = 0 // 출발점 초기화
        dist[startId] = 0 // 출발점 초기화
        while (pq.isNotEmpty()) {
            val point = pq.poll()!! // pq가 비어있지 않을 때 들어왔기 때문에, pq가 null이 될 수는 없다
            val startId = point.startId
            for (i in 0 until pointList[startId].size) { // 연결된 값들 pq에 추가
                // 기존에 있던 값(dist[pointList[startId][i].endId])이
                // 현재 위치한 노드의 dist(dist[pointList[startId][i].startId])에
                // 연결된 노드의 가중치(pointList[startId][i].weight)의 합이 더 작으면 초기화한다.
                // 쉽게 말해서 새로 탐색하려는 dist가 더 작으면 그 작은 값으로 해당 Point를 초기화 시켜주는 것.
                // 그리고 pq에 추가까지.
                if (dist[pointList[startId][i].endId] > dist[startId] + pointList[startId][i].weight) {
                    dist[pointList[startId][i].endId] =
                        dist[pointList[startId][i].startId] + pointList[startId][i].weight
                    from[pointList[startId][i].endId] = pointList[startId][i].startId
                    // 업데이트 된 노드의 연결된 노드를 다음부터 탐색하기 위해 추가한다.
                    for (j in pointList[pointList[startId][i].endId]) {
                        // 18, 152,162,163번 건물은 통과할 수 있는 건물이 아니므로 pq에 넣지 않는다.
                        if ((j.startId == 18 && endId != 18) || (j.startId == 152 && endId != 152) || (j.startId == 162 && endId != 162) || (j.startId == 163 && endId != 163) || (j.startId == 246 && endId != 246) || (j.startId == 255 && endId != 255) || (j.startId == 236 && endId != 236) || (j.startId == 267 && endId != 267) || (j.startId == 14 && endId != 14) || (j.startId == 22 && endId != 22)) {
                            continue
                        }
                        pq.add(Temp(j.startId, j.endId, j.weight))
                    }
                }
            }
        }
        var temp = endId
        finalPath.add(endId)
        Log.e("finalPath", finalPath.toString())
        while (true) {
            temp = from[temp]
            finalPath.add(temp)
            if (temp == startId) {
                break
            }
        }
        println("최종 경로 : $finalPath")
        Log.e("weight sum", dist[endId].toString())

        //다익스트라로 최단 경로를 찾은 후, 그림을 그려주는 부분
        drawLines(startId, endId)
    }

    private fun drawLines(startId: Int, endId: Int) {
        val markerOption = MarkerOptions()
        val latStartPoint = coordinateList[startId - 1].latitude
        val latEndPoint = coordinateList[endId - 1].latitude
        val lngStartPoint = coordinateList[startId - 1].longitude
        val lngEndPoint = coordinateList[endId - 1].longitude

        for (i in 0 until finalPath.size - 1) {
            val cur = finalPath[i] - 1 // 1씩 빼줘야 하는 이유는 coordinatelist에서는 0부터 값이 들어가있기 때문.
            val next = finalPath[i + 1] - 1
            // 여기서와 마커를 찍을 때, 즉 coordinateList에서만 찍으면 되므로 그냥 1을 여기서 빼주는 식으로 처리
            val latStart = coordinateList[cur].latitude
            val latEnd = coordinateList[next].latitude
            val lngStart = coordinateList[cur].longitude
            val lngEnd = coordinateList[next].longitude

            polyLine = mMap.addPolyline(
                PolylineOptions().clickable(true).add(
                    LatLng(latStart, lngStart),
                    LatLng(latEnd, lngEnd),
                )
            )

        }

        //시작점과 출발점에 마크 추가
        markerOption.position(LatLng(latStartPoint, lngStartPoint))
            .title(coordinateList[startId - 1].kor_name)
        mMap.addMarker(markerOption)
        markerOption.position(LatLng(latEndPoint, lngEndPoint))
            .title(coordinateList[endId - 1].kor_name)
        mMap.addMarker(markerOption)
    }

    /**addPoint 함수에서는 점을 잇고 점 사이의 거리를 위도 경도 차로 구해서 가중치로 넣는다.**/
    private fun addPoint(startId: Int, endId: Int) {
        val startLoc = Location("Start Point")
        startLoc.latitude = coordinateList[startId - 1].latitude
        startLoc.longitude = coordinateList[startId - 1].longitude
        val endLoc = Location("End Point")
        endLoc.latitude = coordinateList[endId - 1].latitude
        endLoc.longitude = coordinateList[endId - 1].longitude

        val distance = startLoc.distanceTo(endLoc).toInt() // 두 점 사이의 거리를 미터(Int)로 바꿔 가중치로 넣는다.
        pointList[startId].add(Temp(startId, endId, distance))
        pointList[endId].add(Temp(endId, startId, distance))
    }

    private fun getHeight(context: Context): Int {
        val height: Int
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayMetrics = DisplayMetrics()
            val display = context.display
            display!!.getRealMetrics(displayMetrics)
            displayMetrics.heightPixels
        } else {
            val displayMetrics = DisplayMetrics()
            this.windowManager.defaultDisplay.getMetrics(displayMetrics)
            height = displayMetrics.heightPixels
            height
        }
    }

    //액티비티 시작할 때 권한 없으면 물어보고, 승인을 해주면 해당 위치로 이동
    //권한이 있다면 그냥 해당위치로 이동
    override fun onResume() {
        super.onResume()

        var curLocLat = 0.0
        var curLocLng = 0.0

        //권한 요청하는 부분
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                fineLocation
            )

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocation
            )
            //return
        } else {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    Log.e("?", location?.latitude.toString())
                    curLocLat = location?.latitude ?: 36.62905249003887 // 충북대학교 좌표
                    curLocLng = location?.longitude ?: 127.45629718340625
                    Log.e("Current Location", "${curLocLat}, $curLocLng")
                }
        }

        GlobalScope.launch {
            delay(100L)
            runOnUiThread {
                mMap.apply {
                    Log.e("cur", curLocLat.toString())
                    moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(curLocLat, curLocLng),
                            16.0f
                        )
                    )
                }
            }
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap // 변수 googleMap 초기화
    }


    override fun onBackPressed() {
        val screenHeight = getHeight(baseContext)
        val upSize = (screenHeight.toFloat() / 7f) // 화면 중 1/7만큼을 차지하는 윗 부분
        val downSize = (screenHeight.toFloat() / 7f) * 6f // 화면 중 6/7만큼을 차지하는 아래 부분
        if (cameDown) {
            binding.upLinear.animate().translationY(-upSize).withLayer().duration = 500
            binding.downLinear.animate().translationY(downSize).withLayer().duration = 500
            binding.comeDownBtn.isClickable = true // 다시 뷰에 보이기 때문에 클릭 가능하게 설정
            Log.d("comeDownBtn", "true")
            cameDown = false
        } else {
            super.onBackPressed()
        }
    }

    private fun addWeight() {
        Log.d("addWeight", "addWeight In")
        addPoint(1, 3)
        addPoint(1, 37)
        addPoint(2, 3)
        addPoint(3, 25)
        addPoint(3, 36)
        addPoint(4, 7)
        addPoint(5, 6)
        addPoint(5, 7)
        addPoint(5, 8)
        addPoint(5, 15)
        addPoint(6, 37)
        addPoint(8, 9)
        addPoint(3, 2)
        addPoint(4, 11)
        addPoint(4, 12)
        addPoint(5, 15)
        addPoint(7, 13)
        addPoint(7, 47)
        addPoint(8, 35)
        addPoint(9, 10)
        addPoint(9, 35)
        addPoint(9, 26)
        addPoint(9, 36)
        addPoint(14, 26)
        addPoint(14, 33)
        addPoint(14, 34)
        addPoint(15, 27)
        addPoint(15, 35)
        addPoint(16, 27)
        addPoint(16, 35)
        addPoint(17, 25)
        addPoint(18, 19)
        addPoint(18, 28)
        addPoint(18, 29)
        addPoint(18, 30)
        addPoint(18, 71)
        addPoint(19, 29)
        addPoint(19, 30)
        addPoint(19, 24)
        addPoint(20, 21)
        addPoint(20, 24)
        addPoint(20, 31)
        addPoint(21, 32)
        addPoint(22, 33)
        addPoint(22, 34)
        addPoint(24, 30)
        addPoint(25, 34)
        addPoint(27, 28)
        addPoint(27, 35)
        addPoint(28, 29)
        addPoint(28, 69)
        addPoint(28, 152)
        addPoint(28, 156)
        addPoint(28, 279)
        addPoint(278, 279)
        addPoint(279, 70)
        addPoint(29, 30)
        addPoint(30, 31)
        addPoint(31, 32)
        addPoint(32, 33)
        addPoint(33, 34)
        addPoint(38, 50)
        addPoint(38, 52)
        addPoint(38, 58)
        addPoint(39, 52)
        addPoint(40, 48)
        addPoint(41, 57)
        addPoint(42, 56)
        addPoint(43, 57)
        addPoint(44, 57)
        addPoint(45, 49)
        addPoint(46, 58)
        addPoint(47, 48)
        addPoint(48, 49)
        addPoint(47, 48)
        addPoint(49, 50)
        addPoint(51, 50)
        addPoint(51, 52)
        addPoint(51, 66)
        addPoint(51, 67)
        addPoint(52, 53)
        addPoint(53, 54)
        addPoint(54, 55)
        addPoint(55, 56)
        addPoint(55, 59)
        addPoint(56, 57)
        addPoint(58, 59)
        addPoint(60, 68)
        addPoint(60, 78)
        addPoint(60, 81)
        addPoint(61, 78)
        addPoint(62, 69)
        addPoint(63, 159)
        addPoint(159, 73)
        addPoint(159, 76)
        addPoint(159, 135)
        addPoint(64, 160)
        addPoint(160, 71)
        addPoint(160, 72)
        addPoint(160, 74)
        addPoint(65, 74)
        addPoint(65, 76)
        addPoint(66, 67)
        addPoint(66, 77)
        addPoint(67, 68)
        addPoint(68, 69)
        addPoint(69, 156)
        addPoint(70, 71)
        addPoint(70, 72)
        addPoint(71, 75)
        addPoint(72, 73)
        addPoint(72, 152)
        addPoint(73, 152)
        addPoint(73, 158)
        addPoint(74, 75)
        addPoint(76, 161)
        addPoint(77, 81)
        addPoint(78, 79)
        addPoint(78, 153)
        addPoint(79, 80)
        addPoint(79, 154)
        addPoint(80, 81)
        addPoint(80, 113)
        addPoint(80, 114)
        addPoint(80, 155)
        addPoint(82, 105)
        addPoint(82, 106)
        addPoint(83, 109)
        addPoint(84, 110)
        addPoint(85, 111)
        addPoint(85, 112)
        addPoint(86, 112)
        addPoint(87, 108)
        addPoint(88, 107)
        addPoint(89, 115)
        addPoint(90, 113)
        addPoint(90, 118)
        addPoint(91, 115)
        addPoint(92, 135)
        addPoint(93, 133)
        addPoint(93, 147)
        addPoint(92, 136)
        addPoint(94, 125)
        addPoint(95, 124)
        addPoint(96, 125)
        addPoint(97, 126)
        addPoint(98, 127)
        addPoint(99, 132)
        addPoint(100, 134)
        addPoint(101, 139)
        addPoint(102, 148)
        addPoint(103, 140)
        addPoint(104, 142)
        addPoint(105, 77)
        addPoint(105, 106)
        addPoint(106, 107)
        addPoint(108, 77)
        addPoint(108, 109)
        addPoint(109, 110)
        addPoint(110, 111)
        addPoint(111, 112)
        addPoint(111, 137)
        addPoint(113, 115)
        addPoint(113, 116)
        addPoint(114, 130)
        addPoint(114, 152)
        addPoint(114, 155)
        addPoint(115, 121)
        addPoint(116, 118)
        addPoint(117, 118)
        addPoint(117, 120)
        addPoint(119, 138)
        addPoint(119, 120)
        addPoint(119, 122)
        addPoint(120, 121)
        addPoint(121, 122)
        addPoint(122, 123)
        addPoint(123, 124)
        addPoint(123, 128)
        addPoint(124, 125)
        addPoint(125, 126)
        addPoint(126, 127)
        addPoint(128, 129)
        addPoint(128, 131)
        addPoint(129, 130)
        addPoint(129, 136)
        addPoint(130, 135)
        addPoint(130, 152)
        addPoint(130, 158)
        addPoint(131, 132)
        addPoint(132, 134)
        addPoint(132, 133)
        addPoint(134, 139)
        addPoint(135, 149)
        addPoint(135, 158)
        addPoint(136, 147)
        addPoint(137, 138)
        addPoint(139, 144)
        addPoint(139, 148)
        addPoint(140, 148)
        addPoint(140, 141)
        addPoint(141, 142)
        addPoint(142, 143)
        addPoint(143, 144)
        addPoint(143, 145)
        addPoint(145, 147)
        addPoint(145, 181)
        addPoint(146, 161)
        addPoint(146, 162)
        addPoint(146, 181)
        addPoint(146, 168)
        addPoint(147, 151)
        addPoint(149, 150)
        addPoint(149, 161)
        addPoint(150, 151)
        addPoint(152, 153)
        addPoint(152, 157)
        addPoint(153, 154)
        addPoint(153, 157)
        addPoint(154, 155)
        addPoint(156, 157)
        addPoint(162, 168)
        addPoint(162, 176)
        addPoint(162, 178)
        addPoint(162, 179)
        addPoint(162, 180)
        addPoint(163, 168)
        addPoint(163, 169)
        addPoint(163, 176)
        addPoint(164, 172)
        addPoint(164, 175)
        addPoint(165, 185)
        addPoint(165, 187)
        addPoint(166, 182)
        addPoint(166, 186)
        addPoint(167, 177)
        addPoint(168, 76)
        addPoint(168, 169)
        addPoint(168, 176)
        addPoint(169, 65)
        addPoint(169, 170)
        addPoint(170, 75)
        addPoint(170, 171)
        addPoint(276, 277)
        addPoint(171, 277)
        addPoint(172, 277)
        addPoint(172, 173)
        addPoint(173, 174)
        addPoint(173, 186)
        addPoint(174, 175)
        addPoint(174, 177)
        addPoint(175, 176)
        addPoint(177, 178)
        addPoint(178, 186)
        addPoint(178, 179)
        addPoint(179, 180)
        addPoint(180, 181)
        addPoint(180, 183)
        addPoint(182, 183)
        addPoint(183, 184)
        addPoint(184, 143)
        addPoint(184, 145)
        addPoint(184, 185)
        addPoint(185, 187)
        addPoint(186, 188)
        addPoint(186, 193)
        addPoint(187, 188)
        addPoint(187, 191)
        addPoint(189, 190)
        addPoint(189, 191)
        addPoint(141, 190)
        addPoint(191, 192)
        addPoint(193, 194)
        addPoint(193, 200)
        addPoint(193, 201)
        addPoint(193, 216)
        addPoint(194, 195)
        addPoint(195, 196)
        addPoint(195, 199)
        addPoint(197, 199)
        addPoint(198, 199)
        addPoint(200, 203)
        addPoint(201, 202)
        addPoint(202, 203)
        addPoint(203, 213)
        addPoint(213, 214)
        addPoint(213, 275)
        addPoint(214, 215)
        addPoint(215, 207)
        addPoint(201, 208)
        addPoint(204, 208)
        addPoint(209, 208)
        addPoint(209, 210)
        addPoint(209, 211)
        addPoint(212, 211)
        addPoint(212, 205)
        addPoint(212, 206)
        addPoint(210, 202)
        addPoint(210, 205)
        addPoint(216, 217)
        addPoint(217, 218)
        addPoint(218, 219)
        addPoint(219, 220)
        addPoint(220, 221)
        addPoint(220, 280)
        addPoint(220, 223)
        addPoint(224, 223)
        addPoint(224, 225)
        addPoint(224, 227)
        addPoint(224, 236)
        addPoint(226, 225)
        addPoint(226, 227)
        addPoint(226, 280)
        addPoint(225, 222)
        addPoint(229, 230)
        addPoint(229, 231)
        addPoint(229, 228)
        addPoint(229, 236)
        addPoint(228, 227)
        addPoint(228, 262)
        addPoint(230, 232)
        addPoint(230, 233)
        addPoint(230, 223)
        addPoint(231, 232)
        addPoint(231, 233)
        addPoint(235, 236)
        addPoint(235, 232)
        addPoint(235, 238)
        addPoint(235, 242)
        addPoint(239, 240)
        addPoint(239, 238)
        addPoint(239, 242)
        addPoint(240, 243)
        addPoint(240, 246)
        addPoint(240, 241)
        addPoint(240, 260)
        addPoint(246, 245)
        addPoint(246, 249)
        addPoint(246, 248)
        addPoint(246, 247)
        addPoint(252, 241)
        addPoint(252, 254)
        addPoint(252, 253)
        addPoint(252, 223)
        addPoint(247, 241)
        addPoint(247, 248)
        addPoint(250, 249)
        addPoint(250, 173)
        addPoint(250, 251)
        addPoint(251, 186)
        addPoint(251, 148)
        addPoint(243, 238)
        addPoint(243, 244)
        addPoint(244, 245)
        addPoint(244, 256)
        addPoint(255, 257)
        addPoint(255, 256)
        addPoint(257, 171)
        addPoint(258, 170)
        addPoint(258, 19)
        addPoint(258, 259)
        addPoint(259, 24)
        addPoint(260, 259)
        addPoint(260, 20)
        addPoint(260, 237)
        addPoint(237, 238)
        addPoint(237, 21)
        addPoint(237, 236)
        addPoint(236, 264)
        addPoint(21, 264)
        addPoint(33, 264)
        addPoint(234, 236)
        addPoint(234, 233)
        addPoint(234, 261)
        addPoint(262, 261)
        addPoint(22, 261)
        addPoint(263, 262)
        addPoint(23, 262)
        addPoint(23, 263)
        addPoint(263, 265)
        addPoint(265, 267)
        addPoint(265, 266)
        addPoint(266, 268)
        addPoint(266, 272)
        addPoint(272, 273)
        addPoint(268, 269)
        addPoint(268, 267)
        addPoint(268, 270)
        addPoint(270, 271)
        addPoint(274, 22)
        addPoint(274, 264)
        addPoint(274, 33)
        addPoint(274, 261)
        addPoint(274, 262)
        addPoint(274, 23)
    }
}