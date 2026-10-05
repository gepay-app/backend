package com.gepe.gepay.ledger.internal.validator;

import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class JournalValidator {

    public static void validate(List<JournalLine> lines){
        if(lines == null || lines.size() < 2){
            throw new ServiceException(LedgerError.JOURNAL_MIN_LINES);
        }

        long debitSum = 0L;
        long creditSum = 0L;

        for(JournalLine line:lines){
            if(line.amount() <=0){
                throw new ServiceException(LedgerError.INVALID_AMOUNT, line.accountCode().getCode());
            }
            if(line.direction() == EntryDirection.DEBIT){
                debitSum += line.amount();
            }else{
                creditSum += line.amount();
            }
        }

        if(debitSum != creditSum){
            throw new ServiceException(LedgerError.UNBALANCED_JOURNAL, debitSum, creditSum);
        }
    }
}
