library(jsonlite)
library(dplyr)

file_path_document <- '/Users/tmartins/projects/yahoo/sw/vespa-examples/blog-recommendation-support/data/blog-job/user_item_cf_cv/product.json'
file_path_user <- '/Users/tmartins/projects/yahoo/sw/vespa-examples/blog-recommendation-support/data/blog-job/user_item_cf_cv/user.json'
file_path_train <- '/Users/tmartins/projects/yahoo/sw/vespa-examples/blog-recommendation-support/data/blog-job/training_and_test_indices/train.txt'
output_file <- '/Users/tmartins/projects/yahoo/sw/vespa-examples/blog-recommendation-support/data/blog-job/nn_model/training_set.txt'

# get ids from documents that have a latent vector
lines <- readLines(file_path_document)
product_ids <- NULL
for (line in lines){
  product_ids <- c(product_ids, fromJSON(txt=line)$post_id)
}

# get ids from users that have a latent vector
lines <- readLines(file_path_user)
user_ids <- NULL
for (line in lines){
  user_ids <- c(user_ids, fromJSON(txt=line)$user_id)
}

# read (product, user) ids used for training
train_ids <- read.delim(file = file_path_train, header = FALSE, stringsAsFactors = FALSE)
colnames(train_ids) <- c("product_id", "user_id")

# filter out product id and user id that does not have latent vectors
temp <- merge(x = train_ids, y = data.frame(product_id = product_ids))
final_positive_train_ids <- merge(x = temp, y = data.frame(user_id = user_ids))

# add positive labels
final_positive_train_ids <- data.frame(final_positive_train_ids, label = 1)

# add noise to the data
clicks_per_user <- final_positive_train_ids %>% group_by(user_id) %>% summarise(number_clicks = sum(label))

unread_proportion <- 10
unread_products <- matrix(NA, unread_proportion*sum(clicks_per_user$number_clicks), 3)
colnames(unread_products) <- c("user_id", "product_id", "label")
count <- 0
for (i in 1:nrow(clicks_per_user)){
  print(paste(i, "/ ", nrow(clicks_per_user)))
  number_itens <- unread_proportion * as.numeric(clicks_per_user[i, "number_clicks"])
  row_index <- count + 1:number_itens
  count <- count + number_itens
  user_id <- clicks_per_user[i, "user_id"]
  new_samples <- sample(x = product_ids, size = unread_proportion * as.numeric(clicks_per_user[i, "number_clicks"]), replace = FALSE)
  unread_products[row_index, ] <- matrix(c(rep(as.numeric(user_id), number_itens), new_samples, rep(0, number_itens)), ncol = 3)
}

# create final dataset
final_train_ids <- rbind(final_positive_train_ids, data.frame(unread_products))
duplicated_rows <- duplicated(x = final_train_ids[, c("user_id", "product_id")])
final_train_ids <- final_train_ids[!duplicated_rows, ]

write.table(x = final_train_ids, file = output_file, sep = "\t", quote = FALSE, row.names = FALSE)
