package com.example.imageencryption2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static androidx.constraintlayout.widget.Constraints.TAG;

public class MainActivity extends AppCompatActivity {

    private Button pickImageButton;
    private ImageView pickedImageView, cipheredImageView;
    private final int PICK_IMAGE_REQUEST = 1;
    private Bitmap selectedBitmap;
    int width, height, bitmapSize;
    byte[] byteArray, blockArray, initVector;
    int blockSize;
    List<byte[]> blockList;
    int[] cipheredBlock = new int[256]; //If I make this List type, an error occurs.(IndexOfBounds)
    int round = 8; //default
    List<Integer> cipheredBlocksAll;
    TextView originalImageTextView,cipheredImageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        define();
    }

    private void define() {

        pickedImageView = findViewById(R.id.pickedImageView);
        pickImageButton = findViewById(R.id.pickImageButton);
        cipheredImageView = findViewById(R.id.cipheredImageView);
        originalImageTextView = findViewById(R.id.originalImageTextView);
        cipheredImageTextView = findViewById(R.id.cipheredImageTextView);

        cipheredBlocksAll=new ArrayList<>();

        pickImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data.getData() != null) {

            Uri selectedImageUri = data.getData();

            //selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            selectedBitmap = getBitmap(selectedImageUri);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            if (selectedBitmap != null) {
                selectedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream);
            }

            pickedImageView.setImageBitmap(selectedBitmap);

            convertBitmapToByte();

            initVector = CKGenerator.sineMap(256);
            Log.i("initializationVector", Arrays.toString(initVector) + "");

            for (int i = 0; i < 8; i++) { // normalde < blockSize olacak
                // Fetch i'th block of blocks
                blockXOR(initVector, getArrayBlocks(i));

                for (int j = 0; j < round; j++) {

                    // Generate random number between 1 and 32(excluded) and get shifted AES-SBox by rows
                    Random random = new Random();
                    int[][] shiftRows = DesAes.shiftRows(random.nextInt(4));//32'de hata veriyor(IndexOfBounds)

                    //Ciphered Image Vector XOR shiftRows(before send flatten array to 1x256 size)
                    blockXOR(flattenArray(shiftRows), getArrayBlocks(i));
                    // Generate Random Generated 1x256 array and XOR cipheredImage array
                    blockXOR(generateRandomArray(), getArrayBlocks(i));

                    // Generate random number between 1 and 32(excluded) and get shifted AES-SBox by rows
                    Random random2 = new Random();
                    int[][] shiftCols = DesAes.shiftColumns(random2.nextInt(4));//32'de hata veriyor(IndexOfBounds)

                    // Ciphered Image Vector XOR shiftCols(before send flatten array to 1x256 size)
                    blockXOR(flattenArray(shiftCols), getArrayBlocks(i));
                    // Generate Random Generated 1x256 array and XOR cipheredImage array
                    blockXOR(generateRandomArray(), getArrayBlocks(i));

                    int part = round % 4;
                    // Permutate subblock
                    DesAes.desPermutation(getArrayBlocks(i), part);

                    } //inner for block

                    initVector = getArrayBlocks(i);
                    } //first for block


                    // We are using RGBA that's why Config is ARGB.8888
                    Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
                    // vector is your int[] of ARGB
                    bitmap.copyPixelsFromBuffer(makeBuffer(cipheredBlocksAll, 256));

                    cipheredImageView.setImageBitmap(bitmap);

                    cipheredImageTextView.setVisibility(View.VISIBLE);
                    originalImageTextView.setVisibility(View.VISIBLE);

                }
            }

            //we can generate IntBuffer from following native method
            private IntBuffer makeBuffer (List<Integer> src, int n){
                IntBuffer dst = IntBuffer.allocate(n * n);
                for (int i = 0; i < n; i++) {
                    dst.put(src.get(i));
                }
                dst.rewind();
                return dst;
            }

            /***
             * STEP 2
             * Convert selected bitmap to byte[] array
             *
             * */
            private byte[] convertBitmapToByte () {
                width = selectedBitmap.getWidth();
                height = selectedBitmap.getHeight();

                bitmapSize = selectedBitmap.getRowBytes() * selectedBitmap.getHeight();
                ByteBuffer byteBuffer = ByteBuffer.allocate(bitmapSize);
                selectedBitmap.copyPixelsToBuffer(byteBuffer);
                byteArray = byteBuffer.array();

                blockSize = byteArray.length / 256;

                Log.i("byteArrayLog", Arrays.toString(byteArray) + "");
                Log.i("byteArraySizeLog", byteArray.length + "");
                Log.i("blockSizeLog", blockSize + "");

                return byteArray;
            }

            /***
             *  STEP 3 and STEP 4
             *  The obtained array is divided  into blocks, each containing 256 pixels.
             *  Blocks are saved into an array list.
             *  copyOfRange is used to separate main image array.
             *
             * */
            private byte[] getArrayBlocks ( int position){

                blockList = new ArrayList<>();
                int chunk = 256; // chunk size to divide (256 value is determined in the proposed algorithm - Step3)
                for (int i = 0; i < byteArray.length; i += chunk) {

                    blockArray = Arrays.copyOfRange(byteArray, i, Math.min(byteArray.length, i + chunk));
                    blockList.add(blockArray);
                    // Log.i("blockArraysLog", Arrays.toString(Arrays.copyOfRange(byteArray, i, Math.min(byteArray.length, i + chunk))));
                }

                Log.i("blockArrayLog", Arrays.toString(blockList.get(position)));
                return blockList.get(position);

            }

            /***
             *  STEP 7
             * XOR operation is applied for IV and first block
             * then CipheredImage value is generated.
             */
            private void blockXOR ( byte[] initVector, byte[] blockList){

                for (int i = 0; i < 256; i++) {

                    // cipheredImage.set(i,initVector.get(i).intValue() ^ blockList[i]);
                    cipheredBlock[i] = initVector[i] ^ blockList[i];
                    cipheredBlocksAll.add(cipheredBlock[i]); //deneme
                }
                Log.i("cipheredImage", Arrays.toString(cipheredBlock) + "");

            }

            //for Integer Array
            private void blockXOR ( int[] flatArray, byte[] blockList){

                for (int i = 0; i < 256; i++) {

                    // cipheredImage.set(i,initVector.get(i).intValue() ^ blockList[i]);
                    cipheredBlock[i] = flatArray[i] ^ blockList[i];
                    cipheredBlocksAll.add(cipheredBlock[i]); //deneme

                    Log.i("flatArrayXOR", cipheredBlock[i] + "");
                }
            }

            //Flatten 2D array to 1D array...
            private int[] flattenArray ( int[][] array2D){

                int[] oneDArray = new int[array2D.length * array2D.length];
                int s = 0;

                for (int i = 0; i < array2D.length; i++)
                    for (int j = 0; j < array2D.length; j++) {
                        oneDArray[s] = array2D[i][j];
                        s++;
                    }

                Log.i("oneDArray", Arrays.toString(oneDArray));
                return oneDArray;
            }

            /***
             *  STEP 11
             * Using the CKG function, 256 key values are generated
             * ranging from 0 to 255 values. XOR operation
             * is applied for CipheredImage and key values.
             * (But it's generated with random array according to Github codes so I generated random array)
             */
            public int[] generateRandomArray () {

                int[] array = new int[256];
                Random random = new Random();

                for (int i = 0; i < 256; i++) {
                    array[i] = (random.nextInt(1000));
                }
                return array;
            }

            //To decrease bitmap size
            private Bitmap getBitmap (Uri uri){

                InputStream in = null;
                try {
                    final int IMAGE_MAX_SIZE = 1000000; // 1.0MP
                    in = getApplicationContext().getContentResolver().openInputStream(uri);

                    // Decode image size
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(in, null, o);
                    in.close();

                    int scale = 1;
                    while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) >
                            IMAGE_MAX_SIZE) {
                        scale++;
                    }


                    Bitmap bitmap = null;
                    in = getApplicationContext().getContentResolver().openInputStream(uri);
                    if (scale > 1) {
                        scale--;
                        // scale to max possible inSampleSize that still yields an image
                        // larger than target
                        o = new BitmapFactory.Options();
                        o.inSampleSize = scale;
                        bitmap = BitmapFactory.decodeStream(in, null, o);

                        // resize to desired dimensions
                        int height = bitmap.getHeight();
                        int width = bitmap.getWidth();


                        double y = Math.sqrt(IMAGE_MAX_SIZE
                                / (((double) width) / height));
                        double x = (y / height) * width;

                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int) x,
                                (int) y, true);


                        bitmap.recycle();
                        bitmap = scaledBitmap;

                        System.gc();
                    } else {
                        bitmap = BitmapFactory.decodeStream(in);

                    }
                    in.close();

                    return bitmap;

                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                    return null;

                }
            }

        }
