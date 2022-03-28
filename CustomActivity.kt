@file:Suppress("DEPRECATION")

package com.example.anew

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.Image
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.anew.models.BoardSize
import com.example.anew.utils.BitmapScaler
import com.example.anew.utils.EXTRA_SIZE
import com.example.anew.utils.isPermissionGranted
import com.example.anew.utils.requestPermission
import java.io.ByteArrayOutputStream

class CustomActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "CustomActivity"
        private const val PICK_PHOTO_CODE = 156
        private const val READ_EXTERNAL_PHOTOS_CODE = 157
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_LENGTH = 3
        private const val MAX_GAME_LENGTH = 12
    }

    //reference views
    private lateinit var imageSelect: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var gameName: EditText
    private lateinit var boardSize: BoardSize
    private lateinit var adapter: imageSelectAdapter

    private var imagesRequired = -1
    private var chosenImage = mutableListOf<Uri>() // Uri - Uniform Resource Identifier - string that identifies where a particular resource leaves
                                                  //resource is image stored on the phone


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom)


        //initialize referemces
        imageSelect = findViewById(R.id.imageSelect)
        btnSave = findViewById(R.id.btnSave)
        gameName = findViewById(R.id.gameName)

        //pulling data out from intent and cast it
        boardSize =  intent.getSerializableExtra(EXTRA_SIZE) as BoardSize

        imagesRequired =boardSize.getPairs()

        supportActionBar?.title = "choose the number (0/ $imagesRequired)"

        //setting up back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //contains 2 core components as the main activity
        //adapter and layout manager
        imageSelect.layoutManager = GridLayoutManager(this, boardSize.getWidth())
        imageSelect.setHasFixedSize(true)



        //save button set up
        btnSave.setOnClickListener{

            //responsible for taking all data and images to firebase
            saveDataToFirebase()
        }

        //Maximum characters that could be input to the edit text view
        gameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_LENGTH))

        gameName.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                //main interest
                //when user makes modification we will enable the save button
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {}

        })



        //adapter

        //it will take 3 parameters [Context (this) , list of chosen images through uri ,boardsize]
         adapter = imageSelectAdapter(this, chosenImage, boardSize,  object: imageSelectAdapter.ImageClickListener{
            override fun onPlaceHolderCLicked() {
                //user needs to allow  access for the intent to launch
                if (isPermissionGranted(this@CustomActivity,READ_PHOTOS_PERMISSION)) {

                    launchIntentForPhotos()//implicit intent -request to perform an action based on desired action

                }else {
                    requestPermission(this@CustomActivity, READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)
                }
            }

        })

        imageSelect.adapter = adapter
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        if (requestCode == READ_EXTERNAL_PHOTOS_CODE){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                launchIntentForPhotos()
        }else{
            Toast.makeText(this, "Access is required to ur gallery" , Toast.LENGTH_LONG)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }



    //initializing the home button
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { //contains android prefix as it was added
            finish()

            return true
        }
        return  super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //from the parameters
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null ){
            Log.w(TAG, "No data from activity user cancelled " )
            return
        }

        //2 attributes single and multiple data
        val singleUri = data.data
        val clipData = data.clipData //multiple images

        if (clipData != null) {
            Log.i (TAG,  "Clip Dat number of images ${clipData.itemCount}: $clipData")

            // iterate through the clip data
            for (i in 0 until clipData.itemCount){
                val clipItem = clipData.getItemAt(i)
                if (chosenImage.size < imagesRequired) {
                    chosenImage.add(clipItem.uri)
                }
            }
        }else if (singleUri != null){
            Log.i (TAG, "data: $singleUri")
            chosenImage.add(singleUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose images ${chosenImage.size}/$imagesRequired"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        //enable save button
        if (chosenImage.size != imagesRequired) //user hasn't picked enough images
        {
            return false //button should not be eneabled
        }

        if (gameName.text.isBlank() || gameName.text.length < MIN_GAME_LENGTH)
        {
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK) //implicit intent hence the action pick
        intent.type ="image/*" //describing only pick image
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // allows user to pick multiple images
        startActivityForResult(Intent.createChooser(intent ,"choose Images"),PICK_PHOTO_CODE)
        // for this to function we need to give access/permission in manifest
    }
    private fun saveDataToFirebase() {
      Log.i(TAG, "Save Data to Firebase")
        //introduce a loop to iterate over the chosen uri
        for ((index,photoUri) in chosenImage.withIndex()){

            //imageByteArray is what is going to be upload in the firebase storage
            //getImageByteArray method will take care of down grading the image     
            val imageByteArray = getImageByteArray(photoUri)

        }
    }

    //obj down scalling image the user has chosen
    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val  originalBitmap =
             //if the phone os is running android pie or higher original bitmap will be run by the 1st 2 lines
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver,photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        //if running a lower version
            else{
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }

        //log in the original bitmap so we can scale size and height
        Log.i(TAG, "Original width  ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width  ${scaledBitmap.width} and height ${scaledBitmap.height}")

        //compression quality 0-100
        //0 highly compressed
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteOutputStream)
        return byteOutputStream.toByteArray()

    }


}