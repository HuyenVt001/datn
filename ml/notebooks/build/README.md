Sinh lại notebook Colab từ mã nguồn (sửa notebook = sửa file build rồi chạy lại, KHÔNG sửa tay .ipynb):

    cd ml/notebooks
    python build/build_yolo_train_nb.py snapget_yolo_train.ipynb
    python build/check_nb.py snapget_yolo_train.ipynb   # kiểm tra cú pháp từng ô
