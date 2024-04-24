package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.CreditCardRepository;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class CreditCardController {

    // TODO: wire in CreditCard repository here (~1 line)

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private UserRepository userRepository;


    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        // TODO: Create a credit card entity, and then associate that credit card with user with given userId
        //       Return 200 OK with the credit card id if the user exists and credit card is successfully associated with the user
        //       Return other appropriate response code for other exception cases
        //       Do not worry about validating the card number, assume card number could be any arbitrary format and length

        Optional<User> userOptional = userRepository.findById(payload.getUserId());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            CreditCard newCreditCard = new CreditCard();
            newCreditCard.setIssuanceBank(payload.getCardIssuanceBank());
            newCreditCard.setNumber(payload.getCardNumber());
            // one-to-many relationship between User and CreditCard
            newCreditCard.setOwner(user);
            creditCardRepository.save(newCreditCard);
            return new ResponseEntity<>(newCreditCard.getId(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        // TODO: return a list of all credit card associated with the given userId, using CreditCardView class
        //       if the user has no credit card, return empty list, never return null

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            List<CreditCardView> creditCardViews = user.getCreditCards().stream()
                    .map(creditCard -> CreditCardView.builder()
                            .issuanceBank(creditCard.getIssuanceBank())
                            .number(creditCard.getNumber())
                            .build())
                    .collect(Collectors.toList());
            return new ResponseEntity<>(creditCardViews, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        // TODO: Given a credit card number, efficiently find whether there is a user associated with the credit card
        //       If so, return the user id in a 200 OK response. If no such user exists, return 400 Bad Request

        Optional<CreditCard> creditCardOptional = creditCardRepository.findByNumber(creditCardNumber);
        if (creditCardOptional.isPresent()) {
            CreditCard creditCard = creditCardOptional.get();
            int userId = creditCard.getOwner().getId();
            return new ResponseEntity<>(userId, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<String> updateBalance(@RequestBody UpdateBalancePayload[] payloads) {
        //TODO: Given a list of transactions, update credit cards' balance history.
        //      1. For the balance history in the credit card
        //      2. If there are gaps between two balance dates, fill the empty date with the balance of the previous date
        //      3. Given the payload `payload`, calculate the balance different between the payload and the actual balance stored in the database
        //      4. If the different is not 0, update all the following budget with the difference
        //      For example: if today is 4/12, a credit card's balanceHistory is [{date: 4/12, balance: 110}, {date: 4/10, balance: 100}],
        //      Given a balance amount of {date: 4/11, amount: 110}, the new balanceHistory is
        //      [{date: 4/12, balance: 120}, {date: 4/11, balance: 110}, {date: 4/10, balance: 100}]
        //      Return 200 OK if update is done and successful, 400 Bad Request if the given card number
        //        is not associated with a card.

        for (UpdateBalancePayload payload : payloads) {
            Optional<CreditCard> creditCardOptional = creditCardRepository.findByNumber(payload.getCreditCardNumber());
            if (creditCardOptional.isPresent()) {
                CreditCard creditCard = creditCardOptional.get();
                List<BalanceHistory> balanceHistory = creditCard.getBalanceHistory();

                // Check of existing balance records
                if (!balanceHistory.isEmpty()) {
                    // Check of already a balance entry for the same date
                    Optional<BalanceHistory> existingEntryOptional = balanceHistory.stream()
                            .filter(entry -> entry.getDate().isEqual(payload.getBalanceDate()))
                            .findFirst();

                    //  If same date, update it
                    if (existingEntryOptional.isPresent()) {
                        BalanceHistory existingEntry = existingEntryOptional.get();
                        existingEntry.setBalance(payload.getBalanceAmount());
                    } else {
                        // If not, add new balance entry
                        BalanceHistory newEntry = new BalanceHistory();
                        newEntry.setDate(payload.getBalanceDate());
                        newEntry.setBalance(payload.getBalanceAmount());
                        balanceHistory.add(newEntry);
                    }
                } else {
                    // If no balance records, add a new entry
                    BalanceHistory newEntry = new BalanceHistory();
                    newEntry.setDate(payload.getBalanceDate());
                    newEntry.setBalance(payload.getBalanceAmount());
                    balanceHistory.add(newEntry);
                }

                // Sort balance history by date in descending order
                balanceHistory.sort(Comparator.comparing(BalanceHistory::getDate).reversed());

                // Update subsequent balance entries if any gaps
                for (int i = 0; i < balanceHistory.size() - 1; i++) {
                    LocalDate currentDate = balanceHistory.get(i).getDate();
                    LocalDate nextDate = balanceHistory.get(i + 1).getDate();
                    if (!currentDate.minusDays(1).isEqual(nextDate)) {
                        // Fill the gap with the balance of the previous date
                        double previousBalance = balanceHistory.get(i).getBalance();
                        LocalDate dateToAdd = currentDate.minusDays(1);
                        BalanceHistory gapEntry = new BalanceHistory();
                        gapEntry.setDate(dateToAdd);
                        gapEntry.setBalance(previousBalance);
                        balanceHistory.add(i + 1, gapEntry);
                    }
                }

                creditCardRepository.save(creditCard);
            } else {
                return new ResponseEntity<>("Credit card not found", HttpStatus.BAD_REQUEST);
            }
        }

        return new ResponseEntity<>("Balance updated successfully", HttpStatus.OK);
    }
    
}
