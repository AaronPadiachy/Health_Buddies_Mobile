package com.varsitycollege.xbcad.healthbuddies

import ConfirmationDialogFragment
import android.content.ContentValues
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.Transaction

class Store : AppCompatActivity(), ConfirmationDialogFragment.ConfirmationDialogListener {
    private lateinit var currentUserUid: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var pfpRecyclerView: RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)
        currentUserUid = Firebase.auth.currentUser?.uid.orEmpty()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Initialize the banner adapter
        val bannerAdapter = StoreAdapter(ArrayList()) { item ->
            showConfirmationDialog(item)
        }
        recyclerView.adapter = bannerAdapter

        // Initialize the profile picture adapter
        val profilePictureAdapter = ProfilePictureAdapter(ArrayList()) { item ->
            showConfirmationDialog(item)
        }

        // Find the PFPrecyclerView and set the profile picture adapter
        pfpRecyclerView = findViewById(R.id.PFPrecyclerView)
        pfpRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pfpRecyclerView.adapter = profilePictureAdapter

        // Populate store items
        populateStoreItems(bannerAdapter, profilePictureAdapter)
        fetchUserData()
    }

    //region Confirmation Dialog code
    private fun showConfirmationDialog(item: StoreItem) {
        val itemType = if (recyclerView.adapter is StoreAdapter) "banner" else "profilePicture"
        val dialogFragment = ConfirmationDialogFragment.newInstance(itemType)
        dialogFragment.show(supportFragmentManager, "ConfirmationDialog")
    }


    override fun onConfirmClicked(selectedItemType: String) {
        val selectedItem = (recyclerView.adapter as? StoreAdapter)?.getSelectedItem()
        val selectedPfpItem = (pfpRecyclerView.adapter as? ProfilePictureAdapter)?.getSelectedItem()

        if (selectedItem != null) {
            handleBannerPurchase(selectedItem)
        } else if (selectedPfpItem != null) {
            //handleProfilePicturePurchase(selectedPfpItem)
        } else {
            // Handle the case where no item is selected
            Toast.makeText(this@Store, "No item selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBannerPurchase(selectedItem: StoreItem) {
        // Your existing banner purchase logic here
        val selectedPoints = selectedItem.points

        // Reference to the user's currency node
        val userCurrencyRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("userCurrency")

        userCurrencyRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentCurrency = mutableData.getValue(Long::class.java) ?: 0

                // Ensure the user has enough currency to make the purchase
                return if (currentCurrency >= selectedPoints) {
                    mutableData.value = currentCurrency - selectedPoints
                    Transaction.success(mutableData)
                } else {
                    Transaction.abort()
                }
            }

            override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                if (committed) {
                    // Purchase successful
                    Toast.makeText(this@Store, "Item purchased!", Toast.LENGTH_SHORT).show()

                    // Update the PurchasedItems in the database under the new structure
                    val userPurchasedItemsRef = FirebaseDatabase.getInstance().getReference("PurchasedItems")
                        .child(currentUserUid)
                        .child("banner")
                        .child(selectedItem.storeId)

                    // Add additional information to the database
                    userPurchasedItemsRef.child("ID").setValue(selectedItem.storeId)
                    userPurchasedItemsRef.child("ImageUrl").setValue(selectedItem.imageUrl)
                    userPurchasedItemsRef.child("setValueTrue").setValue(true)

                    // Check if the item is still available (not purchased by another user)
                    isItemPurchased(selectedItem) { isPurchased ->
                        if (!isPurchased) {
                            // Refresh user currency display
                            fetchUserData()
                        } else {
                            // The item was purchased by another user
                            // Handle this case if needed
                        }
                    }
                } else {
                    // Insufficient currency or transaction failed
                    Toast.makeText(this@Store, "Purchase failed: Insufficient currency or transaction failed", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

//region Profile Pic purchase
//    private fun handleProfilePicturePurchase(selectedPfpItem: StoreItem) {
//        // Your profile picture purchase logic here
//        // Reference to the user's purchased profile pictures node
//        val userPurchasedPfpRef =
//            FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("PurchasedProfilePictures")
//
//        userPurchasedPfpRef.runTransaction(object : Transaction.Handler {
//            override fun doTransaction(mutableData: MutableData): Transaction.Result {
//                // Check if the profile picture is already purchased
//                if (mutableData.child(selectedPfpItem.storeId).value != null) {
//                    return Transaction.abort() // The item is already purchased
//                }
//
//                // Deduct points for the profile picture purchase
//                val userCurrencyRef =
//                    FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("userCurrency")
//
//                userCurrencyRef.addListenerForSingleValueEvent(object : ValueEventListener {
//                    override fun onDataChange(dataSnapshot: DataSnapshot) {
//                        val currentCurrency = dataSnapshot.getValue(Long::class.java) ?: 0
//
//                        // Move your logic that depends on currentCurrency here
//                        if (currentCurrency >= selectedPfpItem.points) {
//                            // Perform the transaction to update currency and mark the profile picture as purchased
//                            userCurrencyRef.runTransaction(object : Transaction.Handler {
//                                override fun doTransaction(currencyData: MutableData): Transaction.Result {
//                                    val updatedCurrency = currencyData.getValue(Long::class.java) ?: 0
//                                    currencyData.value = updatedCurrency - selectedPfpItem.points
//                                    return Transaction.success(currencyData)
//                                }
//
//                                override fun onComplete(
//                                    databaseError: DatabaseError?,
//                                    committed: Boolean,
//                                    dataSnapshot: DataSnapshot?
//                                ) {
//                                    // Handle the completion if needed
//                                }
//                            })
//                            // Mark the profile picture as purchased
//                            mutableData.child(selectedPfpItem.storeId).value = true
//                            return Transaction.success(mutableData)
//                        } else {
//                            return Transaction.abort() // Insufficient currency
//                        }
//                    }
//
//                    override fun onCancelled(databaseError: DatabaseError) {
//                        // Handle the error
//                        Log.e("FirebaseData", "Error: ${databaseError.message}")
//                        return Transaction.abort() // Transaction failed
//                    }
//                })
//            }
//
//            override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
//                if (committed) {
//                    // Purchase successful
//                    Toast.makeText(this@Store, "Profile picture purchased!", Toast.LENGTH_SHORT).show()
//                    fetchUserData()
//                } else {
//                    // Insufficient currency or profile picture already purchased
//                    Toast.makeText(
//                        this@Store,
//                        "Purchase failed: Insufficient currency or profile picture already purchased",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//        })
//    }
//endregion

    override fun onCancelClicked() {
        // Handle the cancellation logic here
        Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
    }

    //endregion

    private fun fetchUserData() {
        val database = FirebaseDatabase.getInstance()
        val currentUser = Firebase.auth.currentUser

        if (currentUser != null) {
            val userUid = currentUser.uid
            val userRef = database.getReference("Users").child(userUid)

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        val userDetails = dataSnapshot.getValue(data.UserDetails::class.java)

                        if (userDetails != null) {
                            val mycurrency = userDetails.userCurrency

                            Toast.makeText(this@Store, "my currency: ${mycurrency}", Toast.LENGTH_SHORT).show()

                            val currency: TextView = findViewById(R.id.userCurrencyTxt)
                            currency.text = mycurrency.toString()
                        }
                    } else {
                        Log.d(ContentValues.TAG, "User details do not exist")
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e(ContentValues.TAG, "Error reading user details from the database", databaseError.toException())
                }
            })
        }
    }
    private fun populateStoreItems(bannerAdapter: StoreAdapter, profilePictureAdapter: ProfilePictureAdapter) {
        //region Banner Populating
        val mDatabase = FirebaseDatabase.getInstance().getReference("Store").child("Banners")
        mDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val storeItemList = ArrayList<StoreItem>()

                    for (storeSnapshot in snapshot.children) {
                        // Access properties directly from storeSnapshot
                        val name = storeSnapshot.child("Name").getValue(String::class.java)
                        val imageUrl = storeSnapshot.child("ImageUrl").getValue(String::class.java)
                        val points = storeSnapshot.child("Points").getValue(Long::class.java)
                        val storeId = storeSnapshot.key // Get the storeId from the snapshot

                        if (name != null && imageUrl != null && points != null && storeId != null) {
                            val storeItem = StoreItem(imageUrl, name, points, storeId)

                            // Check if the item is not already purchased
                            isItemPurchased(storeItem) { isPurchased ->
                                if (!isPurchased) {
                                    storeItemList.add(storeItem)
                                    // Update adapter data
                                    bannerAdapter.setItems(storeItemList)
                                    bannerAdapter.notifyDataSetChanged()
                                }

                                // Log the imageUrl for debugging
                                Log.d("FirebaseData", "ImageUrl: $imageUrl")
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("FirebaseData", "Error: ${error.message}")
            }
        })
        //endregion Pop Popudsf

        //region Profile Picture Populating
        val pfpDatabase = FirebaseDatabase.getInstance().getReference("Store").child("ProfilePicture")

        pfpDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(pfpSnapshot: DataSnapshot) {
                Log.d("FirebaseData", "Entered onDataChange for pfpDatabase")
                if (pfpSnapshot.exists()) {
                    val pfpItemList = ArrayList<StoreItem>()

                    for (pfpSnapshot in pfpSnapshot.children) {
                        // Access properties directly from storeSnapshot
                        val imageUrl = pfpSnapshot.child("PFP_url").getValue(String::class.java)
                        val points = pfpSnapshot.child("Price").getValue(Long::class.java)
                        val storeId = pfpSnapshot.key //

                        Log.d("FirebaseData", "ImageUrl: $imageUrl, Points: $points, StoreId: $storeId")

                        if (imageUrl != null && points != null && storeId != null) {
                            val pfpItem = StoreItem(imageUrl, "Profile Picture", points, storeId)

                            // Log the values for debugging
                            Log.d("FirebaseData", "PFP Item: $pfpItem")

                            // Check if the item is not already purchased
                            isItemPurchased(pfpItem) { isPurchased ->
                                Log.d("FirebaseData", "isItemPurchased callback called. isPurchased: $isPurchased")
                                if (!isPurchased) {
                                    pfpItemList.add(pfpItem)
                                    // Update Profile Picture adapter data
                                    profilePictureAdapter.setItems(pfpItemList)
                                    profilePictureAdapter.notifyDataSetChanged()

                                    // Log the number of entries and contents of each entry
                                    Log.d("FirebaseData", "Number of entries in pfpItemList: ${pfpItemList.size}")
                                    for (item in pfpItemList) {
                                        Log.d("FirebaseData", "Entry in pfpItemList: $item")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("FirebaseData", "Error: ${error.message}")
            }
        })


        //endregion
    }
    private fun isItemPurchased(storeItem: StoreItem, callback: (Boolean) -> Unit) {
        // Check if the item is already purchased
        val userPurchasedItemsRef = FirebaseDatabase.getInstance().getReference("PurchasedItems")
            .child(currentUserUid)
            .child("banner")
            .child(storeItem.storeId)

        userPurchasedItemsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If the snapshot exists, the item is already purchased
                callback(snapshot.exists())
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the error
                Log.e("FirebaseData", "Error: ${error.message}")
                callback(false) // Assume the item is not purchased in case of an error
            }
        })
    }


}



